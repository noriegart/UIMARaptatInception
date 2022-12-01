package org.silkca;

import static de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState.FINISHED;
import static de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineCapability.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.DataSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineCapability;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationException;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext.Key;
import src.main.gov.va.vha09.grecc.raptat.rn.uima.core.UIMARaptat;
import src.main.gov.va.vha09.grecc.raptat.gg.core.training.TokenSequenceSolutionTrainer;
import src.main.gov.va.vha09.grecc.raptat.rn.misc.BridgeClass;
import src.main.gov.va.vha09.grecc.raptat.rn.misc.SilkAnnotation;

public class SilkRecommender extends RecommendationEngine implements Serializable
{
	private static final long serialVersionUID = -8297438126101451805L;
	public static final Key<ArrayList<SilkAnnotation>> KEY_MODEL = new Key<>("model");
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	private @SpringBean DocumentService documentService;

	public SilkRecommender(Recommender aRecommender) {
		super(aRecommender);
	}

	@Override
	public RecommendationEngineCapability getTrainingCapability() {
		return TRAINING_NOT_SUPPORTED;
	}

	public synchronized void trainSilkRecommender(RecommenderContext aContext, List<CAS> aCasses) throws RecommendationException {
		String[] trainingArguments = new String[] {"-p",
		"C:\\Projects\\SVN_Projects\\RAPTAT_SVN\\trunk\\src\\main\\resources\\PropertyFilesForCommandLine\\Testing\\TrainingForCreatingTestSolution_211002_v02.prop"};
		synchronized(this) {
			try { 
				TokenSequenceSolutionTrainer.parseAndTrain(trainingArguments);
			} catch (Exception e)  {
				Thread.currentThread().interrupt(); 
			}
			finally {
				UIMARaptat.runRaptat(new String[0], null);
				System.out.println("Finished annotating");
				ArrayList<SilkAnnotation> a = new ArrayList<SilkAnnotation>();
				a.addAll(BridgeClass.silkPredictions);
				aContext.put(KEY_MODEL, a);
				BridgeClass.silkPredictions.clear();
				System.out.println("context");
				notifyAll();
			}
		}

	}

	@Override
	public void train(RecommenderContext aContext, List<CAS> aCasses) throws RecommendationException {
		System.out.println("training");
		if (BridgeClass.documentFinished == true) {
			System.out.println("actual training");
			String[] trainingArguments = new String[] {"-p",
			"C:\\Projects\\SVN_Projects\\RAPTAT_SVN\\trunk\\src\\main\\resources\\PropertyFilesForCommandLine\\Testing\\TrainingForCreatingTestSolution_211002_v02.prop"};
			synchronized(this) {
				try { 
					TokenSequenceSolutionTrainer.parseAndTrain(trainingArguments);
				} catch (Exception e)  {
					Thread.currentThread().interrupt(); 
				}
				finally {
					UIMARaptat.runRaptat(new String[0], null);
					System.out.println("Finished annotating");
					ArrayList<SilkAnnotation> a = new ArrayList<SilkAnnotation>();
					a.addAll(BridgeClass.silkPredictions);
					aContext.put(KEY_MODEL, a);
					BridgeClass.silkPredictions.clear();
					System.out.println("context");
					notifyAll();
				}
			}
		}
		BridgeClass.documentFinished = false;
	}

	@Override
	public boolean isReadyForPrediction(RecommenderContext aContext) {
		//return aContext.get(KEY_MODEL).map(Objects::nonNull).orElse(false);
		return true;
	}


	@Override
	public synchronized void predict(RecommenderContext aContext, CAS aCas) throws RecommendationException  {
//		ArrayList<SilkAnnotation> model = aContext.get(KEY_MODEL).orElseThrow(() ->
//        new RecommendationException("Key [" + KEY_MODEL + "] not found in context"));
		
		System.out.println("started filling in suggestions");
		Type predictedType = getPredictedType(aCas);
		Feature predictedFeature = getPredictedFeature(aCas);
		Feature isPredictionFeature = getIsPredictionFeature(aCas);
		Feature scoreFeature = getScoreFeature(aCas);
		Feature scoreExplanationFeature = getScoreExplanationFeature(aCas);
		for (SilkAnnotation ann: BridgeClass.silkPredictions) {
			AnnotationFS annotation = aCas.createAnnotation(predictedType, ann.begin, ann.end);
			annotation.setStringValue(predictedFeature, ann.label);
			annotation.setBooleanValue(isPredictionFeature, true);
			annotation.setDoubleValue(scoreFeature, 1);
			annotation.setStringValue(scoreExplanationFeature, "");
			aCas.addFsToIndexes(annotation);
		}
		
		System.out.println("suggestions inputted");
	}


	@Override
	public EvaluationResult evaluate(List<CAS> aCasses, DataSplitter aDataSplitter) throws RecommendationException {
		return null;
	}

	@Override
	public int estimateSampleCount(List<CAS> aCasses) {
		return 0;
	}

}
