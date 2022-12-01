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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.wicket.ajax.AjaxRequestTarget;

import com.googlecode.wicket.jquery.ui.widget.menu.IMenuItem;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VLazyDetailResult;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;

public interface AnnotationEditorExtension
{
    /**
     * @return get the bean name.
     */
    String getBeanName();

    /**
     * Handle an action.
     */
    default void handleAction(AnnotationActionHandler panel, AnnotatorState aState,
            AjaxRequestTarget aTarget, CAS aCas, VID paramId, String aAction)
        throws AnnotationException, IOException
    {
        // Do nothing by default
    }

    default void renderRequested(AnnotatorState aState)
    {
        // Do nothing by default
    }

    /**
     * Post-process the output during rendering.
     */
    default void render(CAS aCas, AnnotatorState aState, VDocument vdoc, int aWindowBeginOffset,
            int aWindowEndOffset)
    {
        // Do nothing by default
    }

    default void generateContextMenuItems(List<IMenuItem> aItems)
    {
        // Do nothing by default
    }

    default List<VLazyDetailResult> renderLazyDetails(SourceDocument aDocument, User aUser,
            VID aVid, AnnotationFeature aFeature, String aQuery)
    {
        return Collections.emptyList();
    }
}
