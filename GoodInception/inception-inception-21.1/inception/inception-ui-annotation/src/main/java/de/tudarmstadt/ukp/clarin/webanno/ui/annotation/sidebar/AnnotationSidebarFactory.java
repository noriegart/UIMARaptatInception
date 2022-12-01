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
package de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar;

import org.apache.wicket.model.IModel;

import de.agilecoders.wicket.core.markup.html.bootstrap.image.IconType;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;

public interface AnnotationSidebarFactory
{
    /**
     * @return get the bean name.
     */
    String getBeanName();

    String getDisplayName();

    String getDescription();

    IconType getIcon();

    AnnotationSidebar_ImplBase create(String id, IModel<AnnotatorState> aModel,
            final AnnotationActionHandler aActionHandler, final CasProvider aCasProvider,
            AnnotationPage aAnnotationPage);

    /**
     * Override for cases when sidebar should not be available by default
     */
    default boolean available(Project aProject)
    {
        return true;
    }

    /**
     * Override for cases when sidebar should not be added by default
     */
    default boolean applies(AnnotatorState aState)
    {
        return true;
    }
}
