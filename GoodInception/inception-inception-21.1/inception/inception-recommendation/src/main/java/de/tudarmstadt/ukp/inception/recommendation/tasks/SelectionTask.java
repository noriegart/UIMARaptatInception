/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
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
package de.tudarmstadt.ukp.inception.recommendation.tasks;

import static de.tudarmstadt.ukp.clarin.webanno.api.CasUpgradeMode.AUTO_CAS_UPGRADE;
import static de.tudarmstadt.ukp.clarin.webanno.api.casstorage.CasAccessMode.SHARED_READ_ONLY_ACCESS;
import static de.tudarmstadt.ukp.clarin.webanno.support.logging.LogMessage.info;
import static java.text.MessageFormat.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.persistence.NoResultException;

import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.apache.uima.cas.CAS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.casstorage.CasStorageSession;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.logging.LogMessage;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.DataSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.PercentageBasedSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.model.EvaluatedRecommender;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactory;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderEvaluationResultEvent;
import de.tudarmstadt.ukp.inception.recommendation.event.SelectionTaskEvent;
import de.tudarmstadt.ukp.inception.scheduling.SchedulingService;
import de.tudarmstadt.ukp.inception.scheduling.Task;

/**
 * This task evaluates all available classification tools for all annotation layers of the current
 * project. If a classifier exceeds its specific activation f-score limit during the evaluation it
 * is selected for active prediction.
 */
public class SelectionTask
    extends Task
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private @Autowired AnnotationSchemaService annoService;
    private @Autowired DocumentService documentService;
    private @Autowired RecommendationService recommendationService;
    private @Autowired ApplicationEventPublisher appEventPublisher;
    private @Autowired SchedulingService schedulingService;

    private final SourceDocument currentDocument;
    private final List<LogMessage> logMessages = new ArrayList<>();

    public SelectionTask(User aUser, Project aProject, String aTrigger,
            SourceDocument aCurrentDocument)
    {
        super(aUser, aProject, aTrigger);
        currentDocument = aCurrentDocument;
    }

    @Override
    public void execute()
    {
        try (CasStorageSession session = CasStorageSession.open()) {
            Project project = getProject();
            User user = getUser();
            String userName = user.getUsername();

            // Read the CASes only when they are accessed the first time. This allows us to skip
            // reading the CASes in case that no layer / recommender is available or if no
            // recommender requires evaluation.
            LazyInitializer<List<CAS>> casses = new LazyInitializer<List<CAS>>()
            {
                @Override
                protected List<CAS> initialize()
                {
                    return readCasses(project, userName);
                }
            };

            boolean seenRecommender = false;
            for (AnnotationLayer layer : annoService.listAnnotationLayer(getProject())) {
                if (!layer.isEnabled()) {
                    continue;
                }

                List<Recommender> recommenders = recommendationService.listRecommenders(layer);
                if (recommenders == null || recommenders.isEmpty()) {
                    log.trace("[{}][{}]: No recommenders, skipping selection.", userName,
                            layer.getUiName());
                    continue;
                }

                seenRecommender = true;

                List<EvaluatedRecommender> evaluatedRecommenders = new ArrayList<>();

                for (Recommender r : recommenders) {
                    // Make sure we have the latest recommender config from the DB - the one from
                    // the active recommenders list may be outdated
                    Recommender recommender;
                    try {
                        recommender = recommendationService.getRecommender(r.getId());
                    }
                    catch (NoResultException e) {
                        log.info("[{}][{}]: Recommender no longer available... skipping",
                                user.getUsername(), r.getName());
                        continue;
                    }

                    if (!recommender.isEnabled()) {
                        log.debug("[{}][{}]: Disabled - skipping", userName, recommender.getName());
                        continue;
                    }

                    String recommenderName = recommender.getName();

                    try {
                        long start = System.currentTimeMillis();
                        RecommendationEngineFactory<?> factory = recommendationService
                                .getRecommenderFactory(recommender).orElse(null);

                        if (factory == null) {
                            log.error("[{}][{}]: No recommender factory available for [{}]",
                                    user.getUsername(), r.getName(), r.getTool());
                            appEventPublisher.publishEvent(
                                    new SelectionTaskEvent(this, recommender, user.getUsername(),
                                            String.format("No recommender factory available for %s",
                                                    recommender.getTool())));
                            continue;
                        }

                        if (!factory.accepts(recommender.getLayer(), recommender.getFeature())) {
                            log.info(
                                    "[{}][{}]: Recommender configured with invalid layer or feature "
                                            + "- skipping recommender",
                                    user.getUsername(), r.getName());
                            logMessages.add(info(this,
                                    "Recommender [%s] configured with invalid layer or feature - skipping recommender",
                                    recommenderName));
                            evaluatedRecommenders.add(
                                    EvaluatedRecommender.makeInactiveWithoutEvaluation(recommender,
                                            "Invalid layer or feature"));
                            continue;
                        }

                        RecommendationEngine recommendationEngine = factory.build(recommender);

                        if (recommender.isAlwaysSelected()) {
                            log.debug(
                                    "[{}][{}]: Activating [{}] without evaluating - always selected",
                                    userName, recommenderName, recommenderName);
                            logMessages.add(info(this,
                                    "Recommender [%s] activated without evaluating - always selected",
                                    recommenderName));
                            evaluatedRecommenders.add(
                                    EvaluatedRecommender.makeActiveWithoutEvaluation(recommender));
                            continue;
                        }
                        else if (!factory.isEvaluable()) {
                            log.debug(
                                    "[{}][{}]: Activating [{}] without evaluating - not evaluable",
                                    userName, recommenderName, recommenderName);
                            logMessages.add(info(this,
                                    "Recommender [%s] activated without evaluating - not evaluable",
                                    recommenderName));
                            evaluatedRecommenders.add(
                                    EvaluatedRecommender.makeActiveWithoutEvaluation(recommender));
                            continue;
                        }

                        log.info("[{}][{}]: Evaluating...", userName, recommenderName);

                        DataSplitter splitter = new PercentageBasedSplitter(0.8, 10);
                        EvaluationResult result = recommendationEngine.evaluate(casses.get(),
                                splitter);

                        if (result.isEvaluationSkipped()) {
                            String msg = String.format(
                                    "Evaluation of recommender [%s] could not be performed: %s",
                                    recommenderName, result.getErrorMsg().orElse("unknown reason"));
                            log.info("[{}][{}]: {}", user.getUsername(), recommenderName, msg);
                            logMessages.add(LogMessage.warn(this, "%s", msg));
                            evaluatedRecommenders.add(EvaluatedRecommender
                                    .makeInactiveWithoutEvaluation(recommender, msg));
                            continue;
                        }

                        double score = result.computeF1Score();

                        double threshold = recommender.getThreshold();
                        boolean activated;
                        if (score >= threshold) {
                            activated = true;
                            evaluatedRecommenders.add(EvaluatedRecommender.makeActive(recommender,
                                    result,
                                    format("Score {0,number,#.####} >= threshold {1,number,#.####}",
                                            score, threshold)));
                            log.info("[{}][{}]: Activated ({} >= threshold {})", user.getUsername(),
                                    recommenderName, score, threshold);
                            logMessages.add(
                                    info(this, "Recommender [%s] activated (%f >= threshold %f)",
                                            recommenderName, score, threshold));
                        }
                        else {
                            activated = false;
                            log.info("[{}][{}]: Not activated ({} < threshold {})",
                                    user.getUsername(), recommenderName, score, threshold);
                            logMessages.add(
                                    info(this, "Recommender [%s] not activated (%f < threshold %f)",
                                            recommenderName, score, threshold));
                            evaluatedRecommenders.add(EvaluatedRecommender.makeInactive(recommender,
                                    result,
                                    format("Score {0,number,#.####} < threshold {1,number,#.####}",
                                            score, threshold)));
                        }

                        appEventPublisher.publishEvent(new RecommenderEvaluationResultEvent(this,
                                recommender, user.getUsername(), result,
                                System.currentTimeMillis() - start, activated));

                        Optional<String> recError = result.getErrorMsg();
                        SelectionTaskEvent evalEvent = new SelectionTaskEvent(this, recommender,
                                user.getUsername(), result);
                        if (recError.isPresent()) {
                            evalEvent.setErrorMsg(recError.get());
                        }
                        appEventPublisher.publishEvent(evalEvent);
                    }

                    // Catching Throwable is intentional here as we want to continue the execution
                    // even if a particular recommender fails.
                    catch (Throwable e) {
                        log.error("[{}][{}]: Failed", user.getUsername(), recommenderName, e);
                        appEventPublisher.publishEvent(new SelectionTaskEvent(this, recommender,
                                user.getUsername(), e.getMessage()));
                    }
                }

                recommendationService.setEvaluatedRecommenders(user, layer, evaluatedRecommenders);
            }

            if (!seenRecommender) {
                log.trace("[{}]: No recommenders configured, skipping training.", userName);
                return;
            }

            if (!recommendationService.hasActiveRecommenders(user.getUsername(), project)) {
                log.debug("[{}]: No recommenders active, skipping training.", userName);
                return;
            }

            TrainingTask trainingTask = new TrainingTask(user, getProject(),
                    "SelectionTask after activating recommenders", currentDocument);
            trainingTask.inheritLog(logMessages);
            schedulingService.enqueue(trainingTask);
        }
    }

    private List<CAS> readCasses(Project aProject, String aUserName)
    {
        List<CAS> casses = new ArrayList<>();
        for (SourceDocument document : documentService.listSourceDocuments(aProject)) {
            try {
                // We should not have to modify the CASes... right? Fingers crossed.
                CAS cas = documentService.readAnnotationCas(document, aUserName, AUTO_CAS_UPGRADE,
                        SHARED_READ_ONLY_ACCESS);
                casses.add(cas);
            }
            catch (IOException e) {
                log.error("Cannot read annotation CAS.", e);
            }
        }
        return casses;
    }
}
