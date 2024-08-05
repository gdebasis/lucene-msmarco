package stochastic_qpp;

import utils.IndexUtils;
import org.apache.lucene.search.TopDocs;
import qpp.*;
import qrels.Evaluator;
import qrels.Metric;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;

public class StochasticQPPEvaluation {
    List<MsMarcoQuery> queries;
    OneStepRetriever retriever;
    Map<String, TopDocs> topDocsMapWithoutPerturbation;
    Map<String, RankSwapper> rankingSampler;
    QPPMethod qppMethod;
    String qrelsFile;
    double[] targetMetrics;
    Metric targetMetric;
    boolean relevanceAwareSampling;

    //final static int[] CUTOFFS = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
    final static int[] CUTOFFS = {50};
    final static int NUM_SAMPLES = 50;

    public StochasticQPPEvaluation(
            String queryFile, String qrelsFile,
            String resFile, boolean relevanceAwareSampling,
            Metric targetMetric) throws Exception {
        this.qrelsFile = qrelsFile;
        this.relevanceAwareSampling = relevanceAwareSampling;
        this.targetMetric = targetMetric;

        retriever = new OneStepRetriever(queryFile, resFile);
        //retriever.retrieve(); just use the res file to load per-query retrieved tuples

        queries = retriever.getQueryList();
        IndexUtils.init(retriever.getSearcher());

        Evaluator origRankedListEvaluator = new Evaluator(qrelsFile, resFile);

        // Prepare rank transposition sampler for each query
        topDocsMapWithoutPerturbation = origRankedListEvaluator.getAllRetrievedResults().castToTopDocs();
        if (relevanceAwareSampling)
            rankingSampler = new HashMap<>();

        targetMetrics = new double[queries.size()];
        int i = 0;

        for (MsMarcoQuery query : queries) {
            TopDocs topDocs = topDocsMapWithoutPerturbation.get(query.getId());

            if (relevanceAwareSampling) {
                RankSwapper rankSwapper = new RankSwapper(query.getId(), origRankedListEvaluator, topDocs);
                rankingSampler.put(query.getId(), rankSwapper);
            }

            //System.out.println("Original ranking: ");
            //showTopDocs(topDocs);
            targetMetrics[i] = origRankedListEvaluator.compute(query.getId(), targetMetric);
            //System.out.println(targetMetric.toString() + " on orig ranking = " + targetMetrics[i]);
            i++;
        }
    }

    public static void compareTopDocs(TopDocs a, TopDocs b) {
        System.out.println("Comparing");
        for (int i=0; i < a.scoreDocs.length; i++) {
            if (a.scoreDocs[i].doc != b.scoreDocs[i].doc)
                System.out.println(i + ", " + a.scoreDocs[i].doc + ", " + b.scoreDocs[i].doc);
        }
        System.out.println("Done comparing");
    }

    void showTopDocs(TopDocs topDocs) {
        for (int i=0; i < topDocs.scoreDocs.length; i++) {
            System.out.print(String.format("(%d, %.2f) ", topDocs.scoreDocs[i].doc, topDocs.scoreDocs[i].score));
        }
        System.out.println();
    }

    public TauAndSARE evaluateOnInitialRanking(int cutoff) {
        double[] qppEstimates = new double[queries.size()];
        int i=0;

        for (MsMarcoQuery query: queries) {
            qppEstimates[i++] = qppMethod.computeSpecificity(query, topDocsMapWithoutPerturbation.get(query.getId()), cutoff);
        }
        TauAndSARE qppMeasures = new TauAndSARE(targetMetrics, qppEstimates);
        return qppMeasures;
    }

    public TauAndSARE evaluateOnSingleSample(int cutoff) {
        double[] qppEstimates = new double[queries.size()];
        int i=0;
        Map<String, TopDocs> topDocsMap = new HashMap<>();

        for (MsMarcoQuery query : queries) {
            String qid = query.getId();
            TopDocs permutedSample = relevanceAwareSampling?
                    rankingSampler.get(qid).sample() :
                    RankSwapper.shuffle(this.topDocsMapWithoutPerturbation.get(qid));

            topDocsMap.put(qid, permutedSample);
        }

        Evaluator permutationEvaluator = new Evaluator(qrelsFile, topDocsMap);
        double[] evaluatedMetricValues = new double[queries.size()];

        for (MsMarcoQuery query : queries) {
            String qid = query.getId();
            TopDocs permutedSample = topDocsMap.get(qid);
            qppEstimates[i] = qppMethod.computeSpecificity(query, permutedSample, cutoff);

            //System.out.println("After reranking: ");
            //showTopDocs(permutedSample);

            //compareTopDocs(permutedSample, topDocsMapWithoutPerturbation.get(qid));
            evaluatedMetricValues[i] = permutationEvaluator.compute(query.getId(), targetMetric);
            //System.out.println(targetMetric.toString() + " on permuted ranking = " + evaluatedMetricValues[i]);
            i++;
        }

        TauAndSARE qppMeasures = new TauAndSARE(evaluatedMetricValues, qppEstimates);
        return qppMeasures;
    }

    void batchEvaluate() {
        try {
            BufferedWriter tau_w = new BufferedWriter(
                    new FileWriter(String.format("stochastic-qpp/results-%s/%s_%s-tau.dat",
                            relevanceAwareSampling? "rel": "random",
                            qppMethod.name(), targetMetric.toString())));

            for (int cutoff: CUTOFFS) {
                TauAndSARE qppMetrics = evaluateAggregate(tau_w, cutoff, NUM_SAMPLES);
                double delta_tau = qppMetrics.tau;

                BufferedWriter bw = new BufferedWriter(new FileWriter(
                        String.format("stochastic-qpp/results-%s/%s_%s_%d.dat",
                                relevanceAwareSampling? "rel": "random",
                                qppMethod.name(), targetMetric.toString(), cutoff)));

                System.out.println(String.format("Cutoff: %d\tdelta_tau = %.4f", cutoff, delta_tau));
                for (int j = 0; j < qppMetrics.perQuerySARE.length; j++) {
                    bw.write(String.format("%.4f\t%.4f", targetMetrics[j], 1-qppMetrics.perQuerySARE[j]));
                    bw.newLine();
                }
                bw.close();
            }
            tau_w.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void setQppMethod(QPPMethod qppMethod) {
        this.qppMethod = qppMethod;
    }

    public TauAndSARE evaluateAggregate(BufferedWriter bw, int cutoff, int numSamples) {
        double delta_tau = 0, delta_sare[] = new double[this.queries.size()];

        try {
            TauAndSARE qppMeasuresOnInitialRanking = evaluateOnInitialRanking(cutoff);

            double tau_mean = 0;
            for (int i = 0; i < numSamples; i++) {
                TauAndSARE qppMeasuresOnPermutedSamples = evaluateOnSingleSample(cutoff);
                tau_mean += qppMeasuresOnPermutedSamples.tau;

                delta_tau += Math.abs(qppMeasuresOnInitialRanking.tau - qppMeasuresOnPermutedSamples.tau);

                for (int j = 0; j < delta_sare.length; j++) {
                    delta_sare[j] += Math.abs(qppMeasuresOnInitialRanking.perQuerySARE[j] - qppMeasuresOnPermutedSamples.perQuerySARE[j]);
                }
            }

            for (int j = 0; j < delta_sare.length; j++)
                delta_sare[j] /= numSamples;

            System.out.println(String.format("%d\t%.4f\t%.4f\t%.4f", cutoff, qppMeasuresOnInitialRanking.tau, tau_mean/numSamples, delta_tau/numSamples));
            bw.write(String.format("%d\t%.4f\t%.4f\t%.4f", cutoff, qppMeasuresOnInitialRanking.tau, tau_mean/numSamples, delta_tau/numSamples));
            bw.newLine();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        TauAndSARE averageQppMeasures = new TauAndSARE(delta_tau / numSamples, delta_sare);
        return averageQppMeasures;
    }

    public static void main(String[] args) throws Exception {

        //final String resFile = Constants.BM25_Top100_DL1920;
        final String resFile = Constants.ColBERT_Top100_DL1920;

        boolean[] modes = {/*true,*/ false};
        final Metric[] targetMetricNames = {/*Metric.AP, Metric.nDCG*/ Metric.RR};

        for (Metric m: targetMetricNames) {
            for (boolean relAwareSampling : modes) {
                StochasticQPPEvaluation stochasticQppEval =
                        new StochasticQPPEvaluation(
                                Constants.QUERIES_DL1920,
                                Constants.QRELS_DL1920,
                                resFile,
                                relAwareSampling, m);

                QPPMethod[] qppMethods = {
                        new NQCSpecificity(stochasticQppEval.retriever.getSearcher()),
                        new CumulativeNQC(stochasticQppEval.retriever.getSearcher()),
                        new RSDSpecificity(new NQCSpecificity(stochasticQppEval.retriever.getSearcher())),
                        new UEFSpecificity(new NQCSpecificity(stochasticQppEval.retriever.getSearcher()))
                };

                for (QPPMethod qppMethod : qppMethods) {
                    System.out.println(String.format("Evaluating %s on %s with RAS=%s", qppMethod.name(), m.toString(), relAwareSampling));
                    stochasticQppEval.setQppMethod(qppMethod);
                    stochasticQppEval.batchEvaluate();
                }
            }
        }
    }
}
