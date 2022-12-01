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
package de.tudarmstadt.ukp.clarin.webanno.brat.resource;

import static org.apache.wicket.markup.head.JavaScriptHeaderItem.forReference;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.HeaderItem;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

import com.googlecode.wicket.jquery.ui.settings.JQueryUILibrarySettings;

public class BratVisualizerResourceReference
    extends JavaScriptResourceReference
{
    private static final long serialVersionUID = 1L;

    private static final BratVisualizerResourceReference INSTANCE = new BratVisualizerResourceReference();

    /**
     * Gets the instance of the resource reference
     *
     * @return the single instance of the resource reference
     */
    public static BratVisualizerResourceReference get()
    {
        return INSTANCE;
    }

    /**
     * Private constructor
     */
    private BratVisualizerResourceReference()
    {
        super(BratVisualizerResourceReference.class, "visualizer.js");
    }

    @Override
    public List<HeaderItem> getDependencies()
    {
        List<HeaderItem> dependencies = new ArrayList<>(super.getDependencies());

        // CSS
        dependencies.add(CssHeaderItem.forReference(BratCssVisReference.get()));
        dependencies.add(CssHeaderItem.forReference(BratCssUiReference.get()));

        // Libraries
        dependencies.add(forReference(JQueryUILibrarySettings.get().getJavaScriptReference()));
        dependencies.add(forReference(JQuerySvgResourceReference.get()));
        dependencies.add(forReference(JQuerySvgDomResourceReference.get()));
        dependencies.add(forReference(JQueryJsonResourceReference.get()));
        dependencies.add(forReference(JQueryScrollbarWidthReference.get()));

        // BRAT helpers
        dependencies.add(forReference(BratConfigurationResourceReference.get()));
        dependencies.add(forReference(BratUtilResourceReference.get()));

        // BRAT modules
        dependencies.add(forReference(BratDispatcherResourceReference.get()));
        dependencies.add(forReference(BratAjaxResourceReference.get()));

        return dependencies;
    }
}
