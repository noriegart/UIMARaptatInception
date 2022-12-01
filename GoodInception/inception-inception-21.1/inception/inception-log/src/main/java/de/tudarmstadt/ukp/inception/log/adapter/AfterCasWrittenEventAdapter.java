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
package de.tudarmstadt.ukp.inception.log.adapter;

import java.io.IOException;

import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.clarin.webanno.api.event.AfterCasWrittenEvent;
import de.tudarmstadt.ukp.clarin.webanno.support.JSONUtil;
import de.tudarmstadt.ukp.inception.log.model.CasDetails;

@Component
public class AfterCasWrittenEventAdapter
    implements EventLoggingAdapter<AfterCasWrittenEvent>
{
    @Override
    public boolean accepts(Object aEvent)
    {
        return aEvent instanceof AfterCasWrittenEvent;
    }

    @Override
    public long getDocument(AfterCasWrittenEvent aEvent)
    {
        return aEvent.getDocument().getDocument().getId();
    }

    @Override
    public long getProject(AfterCasWrittenEvent aEvent)
    {
        return aEvent.getDocument().getProject().getId();
    }

    @Override
    public String getAnnotator(AfterCasWrittenEvent aEvent)
    {
        return aEvent.getDocument().getUser();
    }

    @Override
    public String getDetails(AfterCasWrittenEvent aEvent) throws IOException
    {
        CasDetails details = new CasDetails(aEvent.getCas());
        return JSONUtil.toJsonString(details);
    }
}
