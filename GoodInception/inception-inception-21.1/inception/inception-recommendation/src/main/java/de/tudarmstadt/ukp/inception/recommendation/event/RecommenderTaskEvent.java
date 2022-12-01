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
package de.tudarmstadt.ukp.inception.recommendation.event;

import org.springframework.context.ApplicationEvent;

import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;

public class RecommenderTaskEvent
    extends ApplicationEvent
{
    private static final long serialVersionUID = 777340980838549414L;

    private final String user;
    private final Recommender recommender;
    private String errorMsg;

    public RecommenderTaskEvent(Object aSource, String aUser, String aError,
            Recommender aRecommender)
    {
        super(aSource);
        user = aUser;
        errorMsg = aError;
        recommender = aRecommender;
    }

    public RecommenderTaskEvent(Object aSource, String aUser, Recommender aRecommender)
    {
        this(aSource, aUser, null, aRecommender);
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public String getUser()
    {
        return user;
    }

    public void setErrorMsg(String aErrorMsg)
    {
        errorMsg = aErrorMsg;
    }

    public Recommender getRecommender()
    {
        return recommender;
    }
}
