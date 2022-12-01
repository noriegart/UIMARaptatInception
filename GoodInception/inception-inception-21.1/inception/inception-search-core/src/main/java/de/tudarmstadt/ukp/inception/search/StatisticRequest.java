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
package de.tudarmstadt.ukp.inception.search;

import java.util.OptionalInt;
import java.util.Set;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;

public class StatisticRequest
{
    private final Project project;
    private final User user;

    private final OptionalInt minTokenPerDoc;
    private final OptionalInt maxTokenPerDoc;

    private Set<AnnotationFeature> features;
    private final String query;

    public StatisticRequest(Project aProject, User aUser, OptionalInt aMinTokenPerDoc,
            OptionalInt aMaxTokenPerDoc, Set<AnnotationFeature> aFeatures, String aQuery)
    {
        project = aProject;
        user = aUser;

        minTokenPerDoc = aMinTokenPerDoc;
        maxTokenPerDoc = aMaxTokenPerDoc;
        query = aQuery;
        features = aFeatures;
    }

    public Project getProject()
    {
        return project;
    }

    public User getUser()
    {
        return user;
    }

    public OptionalInt getMinTokenPerDoc()
    {
        return minTokenPerDoc;
    }

    public OptionalInt getMaxTokenPerDoc()
    {
        return maxTokenPerDoc;
    }

    public Set<AnnotationFeature> getFeatures()
    {
        return features;
    }

    public void addFeature(AnnotationFeature aFeature)
    {
        features.add(aFeature);
    }

    public String getQuery() { return query; }

}
