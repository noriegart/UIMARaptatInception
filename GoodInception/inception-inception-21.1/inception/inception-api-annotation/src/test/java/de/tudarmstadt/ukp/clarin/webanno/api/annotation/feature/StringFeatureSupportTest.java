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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getAddr;
import static org.apache.uima.fit.factory.JCasFactory.createJCasFromPath;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.JCas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.config.PrimitiveUimaFeatureSupportProperties;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.Tag;
import de.tudarmstadt.ukp.clarin.webanno.model.TagSet;

public class StringFeatureSupportTest
{
    private @Mock AnnotationSchemaService schemaService;

    private StringFeatureSupport sut;

    private JCas jcas;
    private AnnotationFS spanFS;
    private AnnotationFeature valueFeature;
    private TagSet valueTagset;

    @BeforeEach
    public void setUp() throws Exception
    {
        initMocks(this);

        sut = new StringFeatureSupport(new PrimitiveUimaFeatureSupportProperties(), schemaService);

        valueFeature = new AnnotationFeature("value", CAS.TYPE_NAME_STRING);

        valueTagset = new TagSet();

        jcas = createJCasFromPath("src/test/resources/desc/type/webannoTestTypes.xml");
        jcas.setDocumentText("label");

        Type spanType = jcas.getCas().getTypeSystem().getType("webanno.custom.Span");
        spanFS = jcas.getCas().createAnnotation(spanType, 0, jcas.getDocumentText().length());
    }

    @Test
    public void thatUsingOutOfTagsetValueInClosedTagsetProducesException() throws Exception
    {
        final String tag = "TAG-NOT-IN-LIST";

        valueFeature.setTagset(valueTagset);
        valueTagset.setCreateTag(false);

        when(schemaService.existsTag(tag, valueTagset)).thenReturn(false);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> sut.setFeatureValue(jcas.getCas(), valueFeature, getAddr(spanFS), tag))
                .withMessageContaining("is not in the tag list");
    }

    @Test
    public void thatUsingOutOfTagsetValueInOpenTagsetAddsNewValue() throws Exception
    {
        final String tag = "TAG-NOT-IN-LIST";

        valueFeature.setTagset(valueTagset);
        valueTagset.setCreateTag(true);

        when(schemaService.existsTag(tag, valueTagset)).thenReturn(false);

        sut.setFeatureValue(jcas.getCas(), valueFeature, getAddr(spanFS), tag);

        verify(schemaService).createTag(new Tag(valueTagset, tag));
    }
}
