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
package de.tudarmstadt.ukp.inception.export;

import static de.tudarmstadt.ukp.clarin.webanno.api.ProjectService.DOCUMENT_FOLDER;
import static de.tudarmstadt.ukp.clarin.webanno.api.ProjectService.PROJECT_FOLDER;
import static de.tudarmstadt.ukp.clarin.webanno.api.ProjectService.SOURCE_FOLDER;
import static de.tudarmstadt.ukp.clarin.webanno.api.ProjectService.withProjectLogger;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.CHAIN_TYPE;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.CURATION_USER;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.createSentence;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.createToken;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.exists;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getRealCas;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectSentences;
import static de.tudarmstadt.ukp.clarin.webanno.api.casstorage.CasAccessMode.EXCLUSIVE_WRITE_ACCESS;
import static de.tudarmstadt.ukp.clarin.webanno.support.ZipUtils.zipFolder;
import static java.io.File.createTempFile;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.factory.CollectionReaderFactory.createReader;
import static org.apache.uima.fit.factory.ConfigurationParameterFactory.addConfigurationParameters;
import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;
import static org.apache.uima.fit.util.FSUtil.setFeature;
import static org.apache.uima.fit.util.LifeCycleUtil.collectionProcessComplete;
import static org.apache.uima.fit.util.LifeCycleUtil.destroy;
import static org.apache.uima.util.CasCreationUtils.mergeTypeSystems;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CasFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.dkpro.core.api.io.JCasFileWriter_ImplBase;
import org.dkpro.core.api.io.ResourceCollectionReaderBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.transaction.annotation.Transactional;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasStorageService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentImportExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.api.config.RepositoryProperties;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.casstorage.CasStorageSession;
import de.tudarmstadt.ukp.clarin.webanno.api.format.FormatSupport;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.TagSet;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.TagsetDescription;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.export.config.DocumentImportExportServiceAutoConfiguration;
import de.tudarmstadt.ukp.inception.export.config.DocumentImportExportServiceProperties;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link DocumentImportExportServiceAutoConfiguration#documentImportExportService}.
 * </p>
 */
public class DocumentImportExportServiceImpl
    implements DocumentImportExportService
{
    static final String FEATURE_BASE_NAME_UI_NAME = "uiName";
    static final String FEATURE_BASE_NAME_NAME = "name";
    static final String FEATURE_BASE_NAME_LAYER = "layer";
    static final String TYPE_NAME_FEATURE_DEFINITION = "de.tudarmstadt.ukp.clarin.webanno.api.type.FeatureDefinition";
    static final String TYPE_NAME_LAYER_DEFINITION = "de.tudarmstadt.ukp.clarin.webanno.api.type.LayerDefinition";

    private static final String EXPORT_CAS = "exportCas";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RepositoryProperties repositoryProperties;
    private final CasStorageService casStorageService;
    private final AnnotationSchemaService annotationService;
    private final DocumentImportExportServiceProperties properties;

    private final List<FormatSupport> formatsProxy;
    private Map<String, FormatSupport> formats;

    private final TypeSystemDescription schemaTypeSystem;

    @Autowired
    public DocumentImportExportServiceImpl(RepositoryProperties aRepositoryProperties,
            @Lazy @Autowired(required = false) List<FormatSupport> aFormats,
            CasStorageService aCasStorageService, AnnotationSchemaService aAnnotationService,
            DocumentImportExportServiceProperties aServiceProperties)
    {
        repositoryProperties = aRepositoryProperties;
        casStorageService = aCasStorageService;
        annotationService = aAnnotationService;
        formatsProxy = aFormats;
        properties = aServiceProperties;

        schemaTypeSystem = createTypeSystemDescription(
                "de/tudarmstadt/ukp/clarin/webanno/api/type/schema-types");
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshedEvent()
    {
        init();
    }

    /* package private */ void init()
    {
        Map<String, FormatSupport> formatMap = new LinkedHashMap<>();

        if (formatsProxy != null) {
            List<FormatSupport> forms = new ArrayList<>(formatsProxy);
            AnnotationAwareOrderComparator.sort(forms);
            forms.forEach(format -> {
                formatMap.put(format.getId(), format);
                log.debug("Found format: {} ({}, {})",
                        ClassUtils.getAbbreviatedName(format.getClass(), 20), format.getId(),
                        readWriteMsg(format));
            });
        }

        log.info("Found [{}] format supports", formatMap.size());

        formats = unmodifiableMap(formatMap);
    }

    private String readWriteMsg(FormatSupport aFormat)
    {
        if (aFormat.isReadable() && !aFormat.isWritable()) {
            return "read only";
        }
        else if (!aFormat.isReadable() && aFormat.isWritable()) {
            return "write only";
        }
        else if (aFormat.isReadable() && aFormat.isWritable()) {
            return "read/write";
        }
        else {
            throw new IllegalStateException(
                    "Format [" + aFormat.getId() + "] must be at least readable or writable.");
        }
    }

    @Override
    public List<FormatSupport> getFormats()
    {
        return unmodifiableList(new ArrayList<>(formats.values()));
    }

    @Override
    @Transactional
    public File exportAnnotationDocument(SourceDocument aDocument, String aUser,
            FormatSupport aFormat, String aFileName, Mode aMode)
        throws UIMAException, IOException, ClassNotFoundException
    {
        return exportAnnotationDocument(aDocument, aUser, aFormat, aFileName, aMode, true);
    }

    @Override
    @Transactional
    public File exportAnnotationDocument(SourceDocument aDocument, String aUser,
            FormatSupport aFormat, String aFileName, Mode aMode, boolean aStripExtension)
        throws UIMAException, IOException, ClassNotFoundException
    {
        return exportAnnotationDocument(aDocument, aUser, aFormat, aFileName, aMode,
                aStripExtension, null);
    }

    @Override
    @Transactional
    public File exportAnnotationDocument(SourceDocument aDocument, String aUser,
            FormatSupport aFormat, String aFileName, Mode aMode, boolean aStripExtension,
            Map<Pair<Project, String>, Object> aBulkOperationContext)
        throws UIMAException, IOException, ClassNotFoundException
    {
        try (var logCtx = withProjectLogger(aDocument.getProject())) {
            Map<Pair<Project, String>, Object> bulkOperationContext = aBulkOperationContext;
            if (bulkOperationContext == null) {
                bulkOperationContext = new HashMap<>();
            }

            String username;
            switch (aMode) {
            case ANNOTATION:
                username = aUser;
                break;
            case CURATION:
                // The merge result will be exported
                username = CURATION_USER;
                break;
            default:
                throw new IllegalArgumentException("Unknown mode [" + aMode + "]");
            }

            // Read file
            File exportFile;
            try (CasStorageSession session = CasStorageSession.openNested()) {
                CAS cas = casStorageService.readCas(aDocument, username);
                exportFile = exportCasToFile(cas, aDocument, aFileName, aFormat, aStripExtension,
                        aBulkOperationContext);
            }

            log.info("Exported annotations {} for user [{}] from project {} " + "using format [{}]",
                    aDocument, aUser, aDocument.getProject(), aFormat.getId());

            return exportFile;
        }
    }

    @Override
    public CAS importCasFromFile(File aFile, Project aProject, String aFormatId)
        throws UIMAException, IOException
    {
        return importCasFromFile(aFile, aProject, aFormatId, null);
    }

    @Override
    public CAS importCasFromFile(File aFile, Project aProject, String aFormatId,
            TypeSystemDescription aFullProjectTypeSystem)
        throws UIMAException, IOException
    {
        TypeSystemDescription tsd = aFullProjectTypeSystem;

        if (tsd == null) {
            tsd = annotationService.getFullProjectTypeSystem(aProject);
        }

        // Prepare a CAS with the project type system
        CAS cas = CasFactory.createCas(tsd);

        // Convert the source document to CAS
        FormatSupport format = getReadableFormatById(aFormatId).orElseThrow(
                () -> new IOException("No reader available for format [" + aFormatId + "]"));

        CollectionReaderDescription readerDescription = format.getReaderDescription(tsd);
        addConfigurationParameters(readerDescription,
                ResourceCollectionReaderBase.PARAM_SOURCE_LOCATION,
                aFile.getParentFile().getAbsolutePath(),
                ResourceCollectionReaderBase.PARAM_PATTERNS, "[+]" + aFile.getName());
        CollectionReader reader = createReader(readerDescription);

        if (!reader.hasNext()) {
            throw new FileNotFoundException(
                    "Source file [" + aFile.getName() + "] not found in [" + aFile.getPath() + "]");
        }
        reader.getNext(cas);

        // Create sentence / token annotations if they are missing - sentences first because
        // tokens are then generated inside the sentences
        splitSenencesIfNecssaryAndCheckQuota(cas, format);
        splitTokensIfNecssaryAndCheckQuota(cas, format);

        log.info("Imported CAS with [{}] tokens and [{}] sentences from file [{}] (size: {} bytes)",
                cas.getAnnotationIndex(getType(cas, Token.class)).size(),
                cas.getAnnotationIndex(getType(cas, Sentence.class)).size(), aFile, aFile.length());

        return cas;
    }

    private void splitTokensIfNecssaryAndCheckQuota(CAS cas, FormatSupport aFormat)
        throws IOException
    {
        Type tokenType = getType(cas, Token.class);

        if (!exists(cas, tokenType)) {
            tokenize(cas);
        }

        if (properties.getMaxTokens() > 0) {
            int tokenCount = cas.getAnnotationIndex(tokenType).size();
            if (tokenCount > properties.getMaxTokens()) {
                throw new IOException("Number of tokens [" + tokenCount + "] exceeds limit ["
                        + properties.getMaxTokens()
                        + "]. Maybe file does not conform to the format [" + aFormat.getName()
                        + "]? Otherwise, increase the global token limit in the settings file.");
            }
        }

        if (!exists(cas, tokenType)) {
            throw new IOException("The document appears to be empty. Unable to detect any "
                    + "tokens. Empty documents cannot be imported.");
        }
    }

    private void splitSenencesIfNecssaryAndCheckQuota(CAS cas, FormatSupport aFormat)
        throws IOException
    {
        Type sentenceType = getType(cas, Sentence.class);

        if (!exists(cas, sentenceType)) {
            splitSentences(cas);
        }

        if (properties.getMaxSentences() > 0) {
            int sentenceCount = cas.getAnnotationIndex(sentenceType).size();
            if (sentenceCount > properties.getMaxSentences()) {
                throw new IOException("Number of sentences [" + sentenceCount + "] exceeds limit ["
                        + properties.getMaxSentences()
                        + "]. Maybe file does not conform to the format [" + aFormat.getName()
                        + "]? Otherwise, increase the global sentence limit in the settings file.");
            }
        }

        if (!exists(cas, sentenceType)) {
            throw new IOException("The document appears to be empty. Unable to detect any "
                    + "sentences. Empty documents cannot be imported.");
        }
    }

    public static void splitSentences(CAS aCas)
    {
        BreakIterator bi = BreakIterator.getSentenceInstance(Locale.US);
        bi.setText(aCas.getDocumentText());
        int last = bi.first();
        int cur = bi.next();
        while (cur != BreakIterator.DONE) {
            int[] span = new int[] { last, cur };
            trim(aCas.getDocumentText(), span);
            if (!isEmpty(span[0], span[1])) {
                aCas.addFsToIndexes(createSentence(aCas, span[0], span[1]));
            }
            last = cur;
            cur = bi.next();
        }
    }

    public static void tokenize(CAS aCas)
    {
        BreakIterator bi = BreakIterator.getWordInstance(Locale.US);
        for (AnnotationFS s : selectSentences(aCas)) {
            bi.setText(s.getCoveredText());
            int last = bi.first();
            int cur = bi.next();
            while (cur != BreakIterator.DONE) {
                int[] span = new int[] { last, cur };
                trim(s.getCoveredText(), span);
                if (!isEmpty(span[0], span[1])) {
                    aCas.addFsToIndexes(
                            createToken(aCas, span[0] + s.getBegin(), span[1] + s.getBegin()));
                }
                last = cur;
                cur = bi.next();
            }
        }
    }

    /**
     * Remove trailing or leading whitespace from the annotation.
     * 
     * @param aText
     *            the text.
     * @param aSpan
     *            the offsets.
     */
    public static void trim(String aText, int[] aSpan)
    {
        String data = aText;

        int begin = aSpan[0];
        int end = aSpan[1] - 1;

        // Remove whitespace at end
        while ((end > 0) && trimChar(data.charAt(end))) {
            end--;
        }
        end++;

        // Remove whitespace at start
        while ((begin < end) && trimChar(data.charAt(begin))) {
            begin++;
        }

        aSpan[0] = begin;
        aSpan[1] = end;
    }

    public static boolean isEmpty(int aBegin, int aEnd)
    {
        return aBegin >= aEnd;
    }

    public static boolean trimChar(final char aChar)
    {
        switch (aChar) {
        case '\n':
            return true; // Line break
        case '\r':
            return true; // Carriage return
        case '\t':
            return true; // Tab
        case '\u200E':
            return true; // LEFT-TO-RIGHT MARK
        case '\u200F':
            return true; // RIGHT-TO-LEFT MARK
        case '\u2028':
            return true; // LINE SEPARATOR
        case '\u2029':
            return true; // PARAGRAPH SEPARATOR
        default:
            return Character.isWhitespace(aChar);
        }
    }

    @Override
    public File exportCasToFile(CAS aCas, SourceDocument aDocument, String aFileName,
            FormatSupport aFormat, boolean aStripExtension)
        throws IOException, UIMAException
    {
        return exportCasToFile(aCas, aDocument, aFileName, aFormat, aStripExtension, null);
    }

    @Override
    public File exportCasToFile(CAS aCas, SourceDocument aDocument, String aFileName,
            FormatSupport aFormat, boolean aStripExtension,
            Map<Pair<Project, String>, Object> aBulkOperationContext)
        throws IOException, UIMAException
    {
        Project project = aDocument.getProject();
        try (var logCtx = withProjectLogger(project)) {
            Map<Pair<Project, String>, Object> bulkOperationContext = aBulkOperationContext;
            if (bulkOperationContext == null) {
                bulkOperationContext = new HashMap<>();
            }

            // Either fetch the type system from the bulk-context or fetch it from the DB and store
            // it in the bulk-context to avoid further lookups in the same bulk operation
            Pair<Project, String> exportTypeSystemKey = Pair.of(project, "exportTypeSystem");
            TypeSystemDescription exportTypeSystem = (TypeSystemDescription) bulkOperationContext
                    .get(exportTypeSystemKey);
            if (exportTypeSystem == null) {
                exportTypeSystem = getTypeSystemForExport(project);
                bulkOperationContext.put(exportTypeSystemKey, exportTypeSystem);
            }

            try (CasStorageSession session = CasStorageSession.openNested()) {
                CAS exportCas = WebAnnoCasUtil.createCas();
                session.add(EXPORT_CAS, EXCLUSIVE_WRITE_ACCESS, exportCas);

                // Update type system the CAS, compact it (remove all non-reachable feature
                // structures) and remove all internal feature structures in the process
                prepareCasForExport(aCas, exportCas, aDocument, exportTypeSystem);

                // Update the source file name in case it is changed for some reason. This is
                // necessary for the writers to create the files under the correct names.
                addOrUpdateDocumentMetadata(exportCas, aDocument, aFileName);

                addLayerAndFeatureDefinitionAnnotations(exportCas, project, bulkOperationContext);

                addTagsetDefinitionAnnotations(exportCas, project, bulkOperationContext);

                File exportTempDir = createTempFile("webanno", "export");
                try {
                    exportTempDir.delete();
                    exportTempDir.mkdirs();

                    AnalysisEngineDescription writer = aFormat.getWriterDescription(project,
                            exportTypeSystem, exportCas);
                    addConfigurationParameters(writer,
                            JCasFileWriter_ImplBase.PARAM_USE_DOCUMENT_ID, true,
                            JCasFileWriter_ImplBase.PARAM_ESCAPE_FILENAME, false,
                            JCasFileWriter_ImplBase.PARAM_TARGET_LOCATION, exportTempDir,
                            JCasFileWriter_ImplBase.PARAM_STRIP_EXTENSION, aStripExtension);

                    // Not using SimplePipeline.runPipeline here now because it internally works
                    // with an aggregate engine which is slow due to
                    // https://issues.apache.org/jira/browse/UIMA-6200
                    AnalysisEngine engine = null;
                    try {
                        engine = createEngine(writer);
                        engine.process(getRealCas(exportCas));
                        collectionProcessComplete(engine);
                    }
                    finally {
                        destroy(engine);
                    }

                    // If the writer produced more than one file, we package it up as a ZIP file
                    File exportFile;
                    if (exportTempDir.listFiles().length > 1) {
                        exportFile = new File(exportTempDir.getAbsolutePath() + ".zip");
                        try {
                            zipFolder(exportTempDir, exportFile);
                        }
                        catch (Exception e) {
                            log.error("Unable to create zip File");
                        }
                    }
                    else {
                        exportFile = new File(exportTempDir.getParent(),
                                exportTempDir.listFiles()[0].getName());
                        copyFile(exportTempDir.listFiles()[0], exportFile);
                    }

                    return exportFile;
                }
                finally {
                    if (exportTempDir != null) {
                        forceDelete(exportTempDir);
                    }
                }
            }
        }
    }

    @Override
    public TypeSystemDescription getTypeSystemForExport(Project aProject)
        throws ResourceInitializationException
    {
        List<TypeSystemDescription> tsds = new ArrayList<>();
        tsds.add(schemaTypeSystem);
        tsds.add(annotationService.getFullProjectTypeSystem(aProject, false));
        return mergeTypeSystems(tsds);
    }

    /**
     * Performs a CAS upgrade and removes all internal feature structures from the CAS. The
     * resulting CAS should be <b>only</b> used for export and never be persisted within the
     * repository.
     */
    public void prepareCasForExport(CAS aSourceCas, CAS aTargetCas, SourceDocument aSourceDocument,
            TypeSystemDescription aFullProjectTypeSystem)
        throws ResourceInitializationException, UIMAException, IOException
    {
        TypeSystemDescription tsd = aFullProjectTypeSystem;
        if (tsd == null) {
            tsd = getTypeSystemForExport(aSourceDocument.getProject());
        }

        annotationService.upgradeCas(aSourceCas, aTargetCas, tsd);
    }

    private List<AnnotationFeature> listSupportedFeatures(Project aProject,
            Map<Pair<Project, String>, Object> aBulkOperationContext)
    {
        Pair<Project, String> exportFeaturesKey = Pair.of(aProject, "exportFeatures");
        @SuppressWarnings("unchecked")
        List<AnnotationFeature> features = (List<AnnotationFeature>) aBulkOperationContext
                .get(exportFeaturesKey);
        if (features == null) {
            features = annotationService.listSupportedFeatures(aProject).stream() //
                    .filter(AnnotationFeature::isEnabled) //
                    .collect(toList());
            aBulkOperationContext.put(exportFeaturesKey, features);
        }

        return features;
    }

    private void addOrUpdateDocumentMetadata(CAS aCas, SourceDocument aDocument, String aFileName)
        throws MalformedURLException, CASException
    {
        // Update the source file name in case it is changed for some reason. This is
        // necessary for the writers to create the files under the correct names.
        File currentDocumentUri = new File(repositoryProperties.getPath().getAbsolutePath() + "/"
                + PROJECT_FOLDER + "/" + aDocument.getProject().getId() + "/" + DOCUMENT_FOLDER
                + "/" + aDocument.getId() + "/" + SOURCE_FOLDER);
        DocumentMetaData documentMetadata = DocumentMetaData.get(aCas.getJCas());
        documentMetadata.setDocumentBaseUri(currentDocumentUri.toURI().toURL().toExternalForm());
        documentMetadata.setDocumentUri(
                new File(currentDocumentUri, aFileName).toURI().toURL().toExternalForm());
        documentMetadata.setCollectionId(currentDocumentUri.toURI().toURL().toExternalForm());
        documentMetadata.setDocumentId(aFileName);
    }

    private void addLayerAndFeatureDefinitionAnnotations(CAS aCas, Project aProject,
            Map<Pair<Project, String>, Object> aBulkOperationContext)
    {
        List<AnnotationFeature> allFeatures = listSupportedFeatures(aProject,
                aBulkOperationContext);

        Type layerDefType = aCas.getTypeSystem().getType(TYPE_NAME_LAYER_DEFINITION);
        Type featureDefType = aCas.getTypeSystem().getType(TYPE_NAME_FEATURE_DEFINITION);

        Map<AnnotationLayer, List<AnnotationFeature>> featuresGroupedByLayer = allFeatures.stream() //
                .collect(groupingBy(AnnotationFeature::getLayer));

        List<AnnotationLayer> layers = featuresGroupedByLayer.keySet().stream()
                .sorted(Comparator.comparing(AnnotationLayer::getName)).collect(toList());

        for (var layer : layers) {
            final var layerDefFs = aCas.createFS(layerDefType);
            setFeature(layerDefFs, FEATURE_BASE_NAME_NAME, layer.getName());
            setFeature(layerDefFs, FEATURE_BASE_NAME_UI_NAME, layer.getUiName());
            aCas.addFsToIndexes(layerDefFs);

            List<AnnotationFeature> features = featuresGroupedByLayer.get(layer).stream()
                    .sorted(Comparator.comparing(AnnotationFeature::getName)).collect(toList());

            for (var feature : features) {
                final var featureDefFs = aCas.createFS(featureDefType);
                setFeature(featureDefFs, FEATURE_BASE_NAME_LAYER, layerDefFs);
                setFeature(featureDefFs, FEATURE_BASE_NAME_NAME, feature.getName());
                setFeature(featureDefFs, FEATURE_BASE_NAME_UI_NAME, feature.getUiName());
                aCas.addFsToIndexes(featureDefFs);
            }
        }
    }

    private void addTagsetDefinitionAnnotations(CAS aCas, Project aProject,
            Map<Pair<Project, String>, Object> aBulkOperationContext)
    {
        List<AnnotationFeature> features = listSupportedFeatures(aProject, aBulkOperationContext);

        for (AnnotationFeature feature : features) {
            TagSet tagSet = feature.getTagset();
            if (tagSet == null || CHAIN_TYPE.equals(feature.getLayer().getType())) {
                continue;
            }
            String aLayer = feature.getLayer().getName();
            String aTagSetName = tagSet.getName();

            Type tagsetType = getType(aCas, TagsetDescription.class);
            Feature layerFeature = tagsetType.getFeatureByBaseName(FEATURE_BASE_NAME_LAYER);
            Feature nameFeature = tagsetType.getFeatureByBaseName(FEATURE_BASE_NAME_NAME);

            boolean tagSetModified = false;
            // modify existing tagset Name
            for (FeatureStructure fs : select(aCas, tagsetType)) {
                String layer = fs.getStringValue(layerFeature);
                String tagSetName = fs.getStringValue(nameFeature);
                if (layer.equals(aLayer)) {
                    // only if the tagset name is changed
                    if (!aTagSetName.equals(tagSetName)) {
                        fs.setStringValue(nameFeature, aTagSetName);
                        aCas.addFsToIndexes(fs);
                    }
                    tagSetModified = true;
                    break;
                }
            }

            if (!tagSetModified) {
                FeatureStructure fs = aCas.createFS(tagsetType);
                fs.setStringValue(layerFeature, aLayer);
                fs.setStringValue(nameFeature, aTagSetName);
                aCas.addFsToIndexes(fs);
            }
        }
    }
}
