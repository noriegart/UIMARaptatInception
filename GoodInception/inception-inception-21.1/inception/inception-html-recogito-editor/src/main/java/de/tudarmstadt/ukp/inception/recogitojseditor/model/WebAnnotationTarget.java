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
package de.tudarmstadt.ukp.inception.recogitojseditor.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class WebAnnotationTarget
{
    private String id;

    private List<WebAnnotationSelector> selector;

    public WebAnnotationTarget()
    {
        // Default
    }

    public WebAnnotationTarget(String aId)
    {
        id = aId;
    }

    public WebAnnotationTarget(int aBegin, int aEnd, String aText)
    {
        selector = new ArrayList<>();
        selector.add(new WebAnnotationTextPositionSelector(aBegin, aEnd));
        // selector.add(new WebAnnotationTextQuoteSelector(aText));
    }

    public String getId()
    {
        return id;
    }

    public void setId(String aId)
    {
        id = aId;
    }

    public List<WebAnnotationSelector> getSelector()
    {
        return selector;
    }

    public void setSelector(List<WebAnnotationSelector> aSelector)
    {
        selector = aSelector;
    }
}
