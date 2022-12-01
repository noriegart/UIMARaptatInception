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
package de.tudarmstadt.ukp.inception.curation.config;

import javax.persistence.EntityManager;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasStorageService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.inception.curation.CurationEditorExtension;
import de.tudarmstadt.ukp.inception.curation.CurationRenderer;
import de.tudarmstadt.ukp.inception.curation.CurationService;
import de.tudarmstadt.ukp.inception.curation.CurationServiceImpl;
import de.tudarmstadt.ukp.inception.curation.merge.AutomaticMergeStrategy;
import de.tudarmstadt.ukp.inception.curation.merge.ManualMergeStrategy;
import de.tudarmstadt.ukp.inception.curation.sidebar.CurationSidebarFactory;

@Configuration
@ConditionalOnProperty(prefix = "curation.sidebar", name = "enabled", havingValue = "true")
public class CurationServiceAutoConfiguration
{
    @Bean(CurationEditorExtension.EXTENSION_ID)
    public CurationEditorExtension curationEditorExtension(
            AnnotationSchemaService aAnnotationService, DocumentService aDocumentService,
            CurationRenderer aCurationRenderer)
    {
        return new CurationEditorExtension(aAnnotationService, aDocumentService, aCurationRenderer);
    }

    @Bean
    public CurationService curationService(EntityManager aEntityManager,
            DocumentService aDocumentService, SessionRegistry aSessionRegistry,
            ProjectService aProjectService, UserDao aUserRegistry,
            CasStorageService aCasStorageService)
    {
        return new CurationServiceImpl(aEntityManager, aDocumentService, aSessionRegistry,
                aProjectService, aUserRegistry, aCasStorageService);
    }

    @Bean("curationSidebar")
    public CurationSidebarFactory curationSidebarFactory(ProjectService aProjectService,
            UserDao aUserService)
    {
        return new CurationSidebarFactory(aProjectService, aUserService);
    }

    @Bean
    public CurationRenderer curationRenderer(CurationService aCurationService,
            LayerSupportRegistry aLayerSupportRegistry, DocumentService aDocumentService,
            UserDao aUserRepository, AnnotationSchemaService aAnnotationService)
    {
        return new CurationRenderer(aCurationService, aLayerSupportRegistry, aDocumentService,
                aUserRepository, aAnnotationService);
    }

    @Bean(AutomaticMergeStrategy.BEAN_NAME)
    public AutomaticMergeStrategy automaticMergeStrategy(CurationService aCurationService,
            AnnotationSchemaService aAnnotationService)
    {
        return new AutomaticMergeStrategy(aCurationService, aAnnotationService);
    }

    @Bean(ManualMergeStrategy.BEAN_NAME)
    public ManualMergeStrategy manualMergeStrategy()
    {
        return new ManualMergeStrategy();
    }
}
