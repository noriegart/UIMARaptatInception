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
package de.tudarmstadt.ukp.clarin.webanno.model;

import de.tudarmstadt.ukp.clarin.webanno.support.PersistentEnum;

/**
 * Annotation anchoring mode.
 */
public enum AnchoringMode
    implements PersistentEnum
{
    /**
     * Any number of characters - allows zero-span annotations as well.
     */
    CHARACTERS("characters", true),

    /**
     * Single token - no zero-span annotations.
     */
    SINGLE_TOKEN("singleToken", false),

    /**
     * Any number of tokens - allows zero-span annotations as well.
     */
    TOKENS("tokens", true),

    /**
     * Any number of sentences - allows zero-span annotations as well.
     */
    SENTENCES("sentences", true);

    private final String id;
    private final boolean zeroSpanAllowed;

    AnchoringMode(String aId, boolean aZeroSpanAllowed)
    {
        id = aId;
        zeroSpanAllowed = aZeroSpanAllowed;
    }

    @Override
    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return getId();
    }

    @Override
    public String toString()
    {
        return getId();
    }

    public boolean isZeroSpanAllowed()
    {
        return zeroSpanAllowed;
    }
}
