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
package de.tudarmstadt.ukp.clarin.webanno.telemetry.matomo;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.clarin.webanno.telemetry.TelemetryService;
import de.tudarmstadt.ukp.clarin.webanno.telemetry.TelemetrySupport;
import de.tudarmstadt.ukp.clarin.webanno.telemetry.model.TelemetrySettings;
import de.tudarmstadt.ukp.clarin.webanno.telemetry.ui.ToggleBox;

public class MatomoTelemetryTraitsEditor
    extends Panel
{
    private static final long serialVersionUID = 794251464227546704L;

    private static final String MID_FORM = "form";

    private @SpringBean TelemetryService telemetryService;

    private final MatomoTelemetryTraits traits;

    public MatomoTelemetryTraitsEditor(String aId, IModel<TelemetrySettings> aSettings)
    {
        super(aId, aSettings);

        TelemetrySettings settings = aSettings.getObject();

        TelemetrySupport<MatomoTelemetryTraits> support = telemetryService
                .getTelemetrySuppport(settings.getSupport()).get();

        traits = support.readTraits(settings);

        Form<MatomoTelemetryTraits> form = new Form<MatomoTelemetryTraits>(MID_FORM,
                CompoundPropertyModel.of(Model.of(traits)))
        {
            private static final long serialVersionUID = 815916036034433206L;

            @Override
            protected void onSubmit()
            {
                super.onSubmit();
                // Need to fetch the support again here since it is not serializable!
                TelemetrySupport<MatomoTelemetryTraits> support = telemetryService
                        .getTelemetrySuppport(settings.getSupport()).get();
                support.writeTraits(aSettings.getObject(), traits);
            }
        };

        form.add(new ToggleBox("enabled"));

        add(form);
    }
}
