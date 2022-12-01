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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("ui")
@ManagedResource
public class AnnotationEditorPropertiesImpl
    implements AnnotationEditorProperties
{
    private boolean tokenLayerEditable;
    private boolean sentenceLayerEditable;
    private boolean rememberLayerEnabled = false;
    private boolean forwardAnnotationEnabled = false;

    @ManagedAttribute
    @Override
    public boolean isTokenLayerEditable()
    {
        return tokenLayerEditable;
    }

    @ManagedAttribute
    public void setTokenLayerEditable(boolean aTokenLayerEditable)
    {
        tokenLayerEditable = aTokenLayerEditable;
    }

    @ManagedAttribute
    @Override
    public boolean isSentenceLayerEditable()
    {
        return sentenceLayerEditable;
    }

    @ManagedAttribute
    public void setSentenceLayerEditable(boolean aSentenceLayerEditable)
    {
        sentenceLayerEditable = aSentenceLayerEditable;
    }

    @Override
    public boolean isForwardAnnotationEnabled()
    {
        return forwardAnnotationEnabled;
    }

    public void setForwardAnnotationEnabled(boolean aForwardAnnotationEnabled)
    {
        forwardAnnotationEnabled = aForwardAnnotationEnabled;
    }

    @Override
    public boolean isRememberLayerEnabled()
    {
        return rememberLayerEnabled;
    }

    public void setRememberLayerEnabled(boolean aRememberLayerEnabled)
    {
        rememberLayerEnabled = aRememberLayerEnabled;
    }
}
