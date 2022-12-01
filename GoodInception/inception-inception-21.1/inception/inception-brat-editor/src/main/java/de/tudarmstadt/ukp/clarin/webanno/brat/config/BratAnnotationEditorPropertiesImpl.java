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
package de.tudarmstadt.ukp.clarin.webanno.brat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("ui.brat")
@ManagedResource
public class BratAnnotationEditorPropertiesImpl
    implements BratAnnotationEditorProperties
{
    private boolean singleClickSelection = false;
    private boolean deferredRendering = false;
    private boolean clientSideProfiling = false;
    private boolean clientSideTraceLog = false;
    private String whiteSpaceReplacementCharacter = REPLACEMENT_CHARACTER;
    private boolean useCasTokens = false;

    @ManagedAttribute
    @Override
    public boolean isSingleClickSelection()
    {
        return singleClickSelection;
    }

    @ManagedAttribute
    public void setSingleClickSelection(boolean aSingleClickSelection)
    {
        singleClickSelection = aSingleClickSelection;
    }

    @ManagedAttribute
    @Override
    public boolean isDeferredRendering()
    {
        return deferredRendering;
    }

    @ManagedAttribute
    public void setDeferredRendering(boolean aDeferredRendering)
    {
        deferredRendering = aDeferredRendering;
    }

    @ManagedAttribute
    @Override
    public boolean isClientSideProfiling()
    {
        return clientSideProfiling;
    }

    @ManagedAttribute
    public void setClientSideProfiling(boolean aClientSideProfiling)
    {
        clientSideProfiling = aClientSideProfiling;
    }

    @ManagedAttribute
    @Override
    public boolean isClientSideTraceLog()
    {
        return clientSideTraceLog;
    }

    @ManagedAttribute
    public void setClientSideTraceLog(boolean aClientSideTraceLog)
    {
        clientSideTraceLog = aClientSideTraceLog;
    }

    @Override
    public String getWhiteSpaceReplacementCharacter()
    {
        return whiteSpaceReplacementCharacter;
    }

    public void setWhiteSpaceReplacementCharacter(String aWhiteSpaceReplacementCharacter)
    {
        whiteSpaceReplacementCharacter = aWhiteSpaceReplacementCharacter;
    }

    @ManagedAttribute
    public void setUseCasTokens(boolean aIgnoreCasTokens)
    {
        useCasTokens = aIgnoreCasTokens;
    }

    @ManagedAttribute
    @Override
    public boolean isUseCasTokens()
    {
        return useCasTokens;
    }
}
