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
package de.tudarmstadt.ukp.inception.curation.sidebar;

import org.apache.wicket.model.IModel;
import org.springframework.beans.factory.annotation.Autowired;

import de.agilecoders.wicket.core.markup.html.bootstrap.image.IconType;
import de.agilecoders.wicket.extensions.markup.html.bootstrap.icon.FontAwesome5IconType;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebarFactory_ImplBase;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebar_ImplBase;
import de.tudarmstadt.ukp.inception.curation.config.CurationServiceAutoConfiguration;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link CurationServiceAutoConfiguration#curationSidebarFactory}.
 * </p>
 */
public class CurationSidebarFactory
    extends AnnotationSidebarFactory_ImplBase
{
    private final ProjectService projectService;
    private final UserDao userService;

    @Autowired
    public CurationSidebarFactory(ProjectService aProjectService, UserDao aUserService)
    {
        projectService = aProjectService;
        userService = aUserService;
    }

    @Override
    public String getDisplayName()
    {
        return "Curation";
    }

    @Override
    public String getDescription()
    {
        return "Allows curation via the annotation page. Only available to curators.";
    }

    @Override
    public IconType getIcon()
    {
        return FontAwesome5IconType.clipboard_s;
    }

    @Override
    public AnnotationSidebar_ImplBase create(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, CasProvider aCasProvider,
            AnnotationPage aAnnotationPage)
    {
        return new CurationSidebar(aId, aModel, aActionHandler, aCasProvider, aAnnotationPage);
    }

    @Override
    public boolean applies(AnnotatorState aState)
    {
        String currentUser = userService.getCurrentUsername();
        boolean isCurator = projectService.isCurator(aState.getProject(), aState.getUser());
        return isCurator && aState.getUser().getUsername().equals(currentUser);
    }
}
