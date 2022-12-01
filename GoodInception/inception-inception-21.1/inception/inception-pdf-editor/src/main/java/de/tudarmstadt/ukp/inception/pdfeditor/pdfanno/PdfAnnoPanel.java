/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.pdfeditor.pdfanno;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase.PAGE_PARAM_DOCUMENT;
import static org.apache.commons.io.FilenameUtils.separatorsToUnix;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.UrlRenderer;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.resource.FileResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.apache.wicket.util.string.StringValue;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.inception.pdfeditor.PdfAnnotationEditor;
import de.tudarmstadt.ukp.inception.pdfeditor.config.PdfEditorProperties;

public class PdfAnnoPanel
    extends Panel
{
    private static final long serialVersionUID = 4202869513273132875L;

    private @SpringBean DocumentService documentService;

    private @SpringBean PdfEditorProperties pdfEditorProperties;

    private AbstractAjaxBehavior pdfProvider;

    private AbstractAjaxBehavior pdftxtProvider;

    private AbstractAjaxBehavior apiProvider;

    public PdfAnnoPanel(String aId, IModel<AnnotatorState> aModel,
            PdfAnnotationEditor aPdfAnnotationEditor)
    {
        super(aId, aModel);

        add(pdfProvider = new AbstractAjaxBehavior()
        {
            private static final long serialVersionUID = 7715393703216199195L;

            @Override
            public void onRequest()
            {
                SourceDocument doc = aModel.getObject().getDocument();

                File pdfFile = documentService.getSourceDocumentFile(doc);

                FileResourceStream resource = new FileResourceStream(pdfFile)
                {
                    private static final long serialVersionUID = 5985138568430773008L;

                    @Override
                    public String getContentType()
                    {
                        return "application/pdf";
                    }
                };

                ResourceStreamRequestHandler handler = new ResourceStreamRequestHandler(resource);
                handler.setFileName(doc.getName());
                handler.setCacheDuration(Duration.ofSeconds(1));
                handler.setContentDisposition(ContentDisposition.INLINE);

                getRequestCycle().scheduleRequestHandlerAfterCurrent(handler);
            }
        });

        add(pdftxtProvider = new AbstractDefaultAjaxBehavior()
        {
            private static final long serialVersionUID = -8676150164372852265L;

            @Override
            public void respond(AjaxRequestTarget aTarget)
            {
                aPdfAnnotationEditor.initialize(aTarget);
                String pdftext = aPdfAnnotationEditor.getPdfExtractFile().getPdftxt();
                SourceDocument doc = aModel.getObject().getDocument();

                StringResourceStream resource = new StringResourceStream(pdftext, "text/plain");

                ResourceStreamRequestHandler handler = new ResourceStreamRequestHandler(resource);
                handler.setFileName(doc.getName() + ".txt");
                handler.setCacheDuration(Duration.ofSeconds(1));
                handler.setContentDisposition(ContentDisposition.INLINE);

                getRequestCycle().scheduleRequestHandlerAfterCurrent(handler);
            }
        });

        add(apiProvider = new AbstractDefaultAjaxBehavior()
        {
            private static final long serialVersionUID = 3816087744638629290L;

            @Override
            protected void respond(AjaxRequestTarget aTarget)
            {
                aPdfAnnotationEditor.handleAPIRequest(aTarget, getRequest().getPostParameters());
            }
        });

        add(new WebMarkupContainer("frame")
        {
            private static final long serialVersionUID = 1421253898149294234L;

            @Override
            protected final void onComponentTag(final ComponentTag aTag)
            {
                checkComponentTag(aTag, "iframe");

                String indexFile = pdfEditorProperties.isDebug() ? "index-debug.html"
                        : "index.html";

                UrlRenderer urlRenderer = RequestCycle.get().getUrlRenderer();

                String viewerUrl = urlRenderer
                        .renderContextRelativeUrl("resources/pdfanno/" + indexFile);

                String pdfUrl = toRelativeUrl(viewerUrl, pdfProvider.getCallbackUrl());
                String pdftxtUrl = toRelativeUrl(viewerUrl, pdftxtProvider.getCallbackUrl());
                String apiUrl = toRelativeUrl(viewerUrl, apiProvider.getCallbackUrl());

                // When accessing the annotator page using a URL in the form with the last
                // path element being the document ID, then the AnnotationPageBase rewrites
                // the URL, stripping the last path element and instead rendering it as a
                // fragment. However, the URLRenderer does not notice this and does not get
                // updated. Thus, we get the wrong relative URL for the viewer and need to
                // fix it here.
                StringValue documentParameter = getPage().getPageParameters()
                        .get(PAGE_PARAM_DOCUMENT);
                if (!documentParameter.isEmpty()) {
                    // Why only the apiUrl? Timing probably. The text and PDF load ok
                    // but at the time when the annotations are loaded, they URL has already
                    // been updated by the AnnotationPageBase causing the client-side JS
                    // to resolve the API URL against the wrong page URL...
                    apiUrl = "../" + apiUrl;
                }

                viewerUrl += "?pdf=" + pdfUrl + "&pdftxt=" + pdftxtUrl + "&api=" + apiUrl;

                aTag.put("src", viewerUrl);

                super.onComponentTag(aTag);
            }
        });
    }

    private String toRelativeUrl(String aViewerUrl, CharSequence aCallbackUrl)
    {
        UrlRenderer urlRenderer = RequestCycle.get().getUrlRenderer();

        Url fullViewerUrl = Url.parse(urlRenderer.renderFullUrl(Url.parse(aViewerUrl)));
        Path fullViewerPath = Path.of(fullViewerUrl.getPath()).getParent();

        Url fullCallbackUrl = Url.parse(urlRenderer.renderFullUrl(Url.parse(aCallbackUrl)));
        Path fullCallbackPath = Path.of(fullCallbackUrl.getPath());

        Path relativeCallbackPath = fullViewerPath.relativize(fullCallbackPath);
        String relativeCallbackUrl = separatorsToUnix(relativeCallbackPath.toString()) + "?"
                + fullCallbackUrl.getQueryString();

        return relativeCallbackUrl;
    }
}
