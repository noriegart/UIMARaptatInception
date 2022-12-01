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
package de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model;

import static de.tudarmstadt.ukp.clarin.webanno.api.CasUpgradeMode.AUTO_CAS_UPGRADE;
import static de.tudarmstadt.ukp.clarin.webanno.api.casstorage.CasAccessMode.SHARED_READ_ONLY_ACCESS;
import static org.apache.wicket.markup.head.JavaScriptHeaderItem.forReference;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.wicket.jquery.ui.settings.JQueryUILibrarySettings;

import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratLazyDetailsLookupService;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratRequestUtils;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratVisualizer;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.NormDataResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratAjaxResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratAnnotatorUiResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratConfigurationResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratCurationUiResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratDispatcherResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratUtilResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratVisualizerResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.BratVisualizerUiResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.JQueryJsonResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.JQuerySvgDomResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.brat.resource.JQuerySvgResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;

/**
 * Wicket panel for visualizing an annotated sentence in brat. When a user clicks on a span or an
 * arc, the Method onSelectAnnotationForMerge() is called. Override that method to receive the
 * result in another Wicket panel.
 */
public abstract class BratSuggestionVisualizer
    extends BratVisualizer
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final long serialVersionUID = 6653508018500736430L;

    private @SpringBean ProjectService projectService;
    private @SpringBean UserDao userService;
    private @SpringBean BratLazyDetailsLookupService lazyDetailsLookupService;
    private @SpringBean DocumentService documentService;

    private AbstractDefaultAjaxBehavior controller;
    private final int position;

    public BratSuggestionVisualizer(String aId, IModel<AnnotatorSegment> aModel, int aPosition)
    {
        super(aId, aModel);

        position = aPosition;

        add(new Label("username", getModel().map(this::maybeAnonymizeUsername)));

        controller = new AbstractDefaultAjaxBehavior()
        {
            private static final long serialVersionUID = 1133593826878553307L;

            @Override
            protected void respond(AjaxRequestTarget aTarget)
            {
                try {
                    final IRequestParameters request = getRequest().getPostParameters();
                    String action = BratRequestUtils.getActionFromRequest(request);
                    final VID paramId = BratRequestUtils.getVidFromRequest(request);

                    if (NormDataResponse.is(action)) {
                        AnnotatorSegment segment = getModelObject();
                        CasProvider casProvider = () -> documentService.readAnnotationCas(
                                segment.getAnnotatorState().getDocument(),
                                segment.getUser().getUsername(), AUTO_CAS_UPGRADE,
                                SHARED_READ_ONLY_ACCESS);
                        var result = lazyDetailsLookupService.actionLookupNormData(request, paramId,
                                casProvider, segment.getAnnotatorState().getDocument(),
                                segment.getUser());

                        try {
                            BratRequestUtils.attachResponse(aTarget, vis, result);
                        }
                        catch (IOException e) {
                            handleError("Unable to produce JSON response", e);
                        }
                    }
                    else {
                        onClientEvent(aTarget);
                    }
                }
                catch (Exception e) {
                    aTarget.addChildren(getPage(), IFeedback.class);
                    error("Error: " + e.getMessage());
                }
            }

        };
        add(controller);
    }

    private String maybeAnonymizeUsername(AnnotatorSegment aSegment)
    {
        Project project = aSegment.getAnnotatorState().getProject();
        if (project.isAnonymousCuration()
                && !projectService.isManager(project, userService.getCurrentUser())) {
            return "Anonymized annotator " + (position + 1);
        }

        return aSegment.getUser().getUiName();
    }

    public void setModel(IModel<AnnotatorSegment> aModel)
    {
        setDefaultModel(aModel);
    }

    public void setModelObject(AnnotatorSegment aModel)
    {
        setDefaultModelObject(aModel);
    }

    @SuppressWarnings("unchecked")
    public IModel<AnnotatorSegment> getModel()
    {
        return (IModel<AnnotatorSegment>) getDefaultModel();
    }

    public AnnotatorSegment getModelObject()
    {
        return (AnnotatorSegment) getDefaultModelObject();
    }

    @Override
    public void renderHead(IHeaderResponse aResponse)
    {
        // MUST NOT CALL super.renderHead here because that would call Util.embedByUrl again!
        // super.renderHead(aResponse);

        // Libraries
        aResponse.render(forReference(JQueryUILibrarySettings.get().getJavaScriptReference()));
        aResponse.render(JavaScriptHeaderItem.forReference(JQuerySvgResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(JQuerySvgDomResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(JQueryJsonResourceReference.get()));

        // BRAT helpers
        aResponse.render(
                JavaScriptHeaderItem.forReference(BratConfigurationResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratUtilResourceReference.get()));
        // aResponse.render(JavaScriptHeaderItem.forReference(
        // BratAnnotationLogResourceReference.get()));
        // aResponse.render(JavaScriptHeaderItem.forReference(BratSpinnerResourceReference.get()));

        // BRAT modules
        aResponse.render(JavaScriptHeaderItem.forReference(BratDispatcherResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratAjaxResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratVisualizerResourceReference.get()));
        aResponse
                .render(JavaScriptHeaderItem.forReference(BratVisualizerUiResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratAnnotatorUiResourceReference.get()));
        aResponse.render(JavaScriptHeaderItem.forReference(BratCurationUiResourceReference.get()));
        // aResponse.render(
        // JavaScriptHeaderItem.forReference(BratUrlMonitorResourceReference.get()));

        // BRAT call to load the BRAT JSON from our collProvider and docProvider.
        // @formatter:off
        String script = 
                "Util.embedByURL(" 
                + "  '" + vis.getMarkupId() + "'," 
                + "  '" + collProvider.getCallbackUrl() + "', " 
                + "  '" + docProvider.getCallbackUrl() + "', " 
                + "  function(dispatcher) {" 
                + "    dispatcher.wicketId = '" + vis.getMarkupId() + "'; " 
                + "    dispatcher.ajaxUrl = '" + controller.getCallbackUrl() + "'; " 
                + "    var ajax = new Ajax(dispatcher);"
                + "    var curation_mod = new CurationMod(dispatcher, '" + vis.getMarkupId() + "');"
                + "    Wicket.$('" + vis.getMarkupId() + "').dispatcher = dispatcher;"
                // + " dispatcher.post('clearSVG', []);"
                + "  });";
        // @formatter:on
        aResponse.render(OnLoadHeaderItem.forScript("\n" + script));
    }

    @Override
    public String getDocumentData()
    {
        return getModelObject().getDocumentResponse() == null ? "{}"
                : getModelObject().getDocumentResponse();
    }

    @Override
    protected String getCollectionData()
    {
        return getModelObject().getCollectionData();
    }

    private void handleError(String aMessage, Exception e)
    {
        RequestCycle requestCycle = RequestCycle.get();
        requestCycle.find(AjaxRequestTarget.class)
                .ifPresent(target -> target.addChildren(getPage(), IFeedback.class));

        if (e instanceof AnnotationException) {
            // These are common exceptions happening as part of the user interaction. We do
            // not really need to log their stack trace to the log.
            error(aMessage + ": " + e.getMessage());
            // If debug is enabled, we'll also write the error to the log just in case.
            if (LOG.isDebugEnabled()) {
                LOG.error("{}: {}", aMessage, e.getMessage(), e);
            }
            return;
        }

        LOG.error("{}", aMessage, e);
        error(aMessage);
    }

    protected abstract void onClientEvent(AjaxRequestTarget aTarget) throws Exception;
}
