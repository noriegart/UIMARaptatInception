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
package de.tudarmstadt.ukp.clarin.webanno.agreement.results.unitizing;

import java.util.List;
import java.util.Map;

import org.apache.uima.cas.CAS;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.danekja.java.util.function.serializable.SerializableSupplier;
import org.dkpro.statistics.agreement.coding.ICodingAnnotationStudy;

import de.tudarmstadt.ukp.clarin.webanno.agreement.PairwiseAnnotationResult;
import de.tudarmstadt.ukp.clarin.webanno.agreement.measures.AgreementMeasureSupport_ImplBase;
import de.tudarmstadt.ukp.clarin.webanno.agreement.measures.DefaultAgreementTraits;

public abstract class AbstractUnitizingAgreementMeasureSupport<T extends DefaultAgreementTraits>
    extends
    AgreementMeasureSupport_ImplBase<T, PairwiseAnnotationResult<UnitizingAgreementResult>, ICodingAnnotationStudy>
{
    @Override
    public Panel createResultsPanel(String aId,
            IModel<PairwiseAnnotationResult<UnitizingAgreementResult>> aResults,
            SerializableSupplier<Map<String, List<CAS>>> aCasMapSupplier)
    {
        return new PairwiseUnitizingAgreementTable(aId, aResults);
    }
}
