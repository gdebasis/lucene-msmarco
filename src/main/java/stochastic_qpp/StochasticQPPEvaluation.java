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
import java.io.IOException;
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
    boolean uniformSampling;
    String samplingModeName;

    //final static int[] CUTOFFS = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
    final static int[] CUTOFFS = {50};
    final static int NUM_SAMPLES = 50;

    public StochasticQPPEvaluation(
            String queryFile, String qrelsFile,
            String resFile, String samplingMode, /* U (uniform), M (metadata), R (rel-aware) */
            Metric targetMetric) throws Exception {
        this.qrelsFile = qrelsFile;

        this.uniformSampling = samplingMode.equals("U");
        this.samplingModeName = uniformSampling? "random": samplingMode.equals("R")? "rel" : "av";
        this.targetMetric = targetMetric;

        retriever = new OneStepRetriever(queryFile, resFile);
        //retriever.retrieve(); just use the res file to load per-query retrieved tuples

        queries = retriever.getQueryList();
        IndexUtils.init(retriever.getSearcher());

        Evaluator origRankedListEvaluator = new Evaluator(qrelsFile, resFile);

        // Prepare rank transposition sampler for each query
        topDocsMapWithoutPerturbation = origRankedListEvaluator.getAllRetrievedResults().castToTopDocs();
        if (!uniformSampling)
            rankingSampler = new HashMap<>();

        targetMetrics = new double[queries.size()];
        int i = 0;

        for (MsMarcoQuery query : queries) {
            TopDocs topDocs = topDocsMapWithoutPerturbation.get(query.getId());

            if (!uniformSampling) {
                RankSwapper lrankSwapper =
                        samplingMode.equals("R")?
                        new RankSwapper(query.getId(), origRankedListEvaluator, topDocs):
                        new AttributeValueBasedSwapper(query.getId(), origRankedListEvaluator, topDocs, null);
                rankingSampler.put(query.getId(), lrankSwapper);
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

    public QPPMetricBundle evaluateOnInitialRanking(int cutoff) {
        double[] qppEstimates = new double[queries.size()];
        int i=0;

        for (MsMarcoQuery query: queries) {
            qppEstimates[i++] = qppMethod.computeSpecificity(query, topDocsMapWithoutPerturbation.get(query.getId()));
        }
        QPPMetricBundle qppMeasures = new QPPMetricBundle(targetMetrics, qppEstimates);
        return qppMeasures;
    }

    public QPPMetricBundle evaluateOnSingleSample(int cutoff, int sampleId) throws IOException {
        double[] qppEstimates = new double[queries.size()];
        int i=0;
        Map<String, TopDocs> topDocsMap = new HashMap<>();

        for (MsMarcoQuery query : queries) {
            String qid = query.getId();
            TopDocs permutedSample = uniformSampling?
                    RankSwapper.shuffle(this.topDocsMapWithoutPerturbation.get(qid)) :
                    rankingSampler.get(qid).sample();

            topDocsMap.put(qid, permutedSample);
        }

        if (Constants.WRITE_PERMS) {
            qppMethod.writePermutationMap(queries, topDocsMap, sampleId);
            return null;
        }

        Evaluator permutationEvaluator = new Evaluator(qrelsFile, topDocsMap);
        double[] evaluatedMetricValues = new double[queries.size()];

        for (MsMarcoQuery query : queries) {
            String qid = query.getId();
            TopDocs permutedSample = topDocsMap.get(qid);

            qppEstimates[i] = qppMethod.computeSpecificity(query, permutedSample);

            //System.out.println("After reranking: ");
            //showTopDocs(permutedSample);

            //compareTopDocs(permutedSample, topDocsMapWithoutPerturbation.get(qid));
            evaluatedMetricValues[i] = permutationEvaluator.compute(query.getId(), targetMetric);
            //System.out.println(targetMetric.toString() + " on permuted ranking = " + evaluatedMetricValues[i]);
            i++;
        }

        QPPMetricBundle qppMeasures = new QPPMetricBundle(evaluatedMetricValues, qppEstimates);
        return qppMeasures;
    }

    void batchEvaluate() {
        try {
            BufferedWriter tau_w = new BufferedWriter(
                    new FileWriter(String.format("stochastic-qpp/results-%s/%s_%s-tau.dat",
                            samplingModeName,
                            qppMethod.name(), targetMetric.toString())));

            for (int cutoff: CUTOFFS) {
                QPPMetricBundle qppMetrics = evaluateAggregate(tau_w, cutoff, NUM_SAMPLES);
                if (qppMetrics == null) continue;
                double delta_tau = qppMetrics.tau;

                BufferedWriter bw = new BufferedWriter(new FileWriter(
                        String.format("stochastic-qpp/results-%s/%s_%s_%d.dat",
                                samplingModeName,
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

    public QPPMetricBundle evaluateAggregate(BufferedWriter bw, int cutoff, int numSamples) {
        double delta_tau = 0, delta_sare[] = new double[this.queries.size()];
        QPPMetricBundle qppMeasuresOnInitialRanking = null;
        try {
            if (!Constants.WRITE_PERMS) {
                qppMethod.setDataSource(String.format("%s/%s.0.tsv", PreComputedPredictor.qppScoreFilePrefix, qppMethod.name()));
                qppMeasuresOnInitialRanking = evaluateOnInitialRanking(cutoff);
                System.out.println("QPP on orig list: " + qppMeasuresOnInitialRanking.tau);
            }

            qppMethod.writePermutationMap(queries, topDocsMapWithoutPerturbation, 0);

            double tau_mean = 0;
            for (int i = 0; i < numSamples; i++) {
                int sampleId = i+1;  /* starts from 1 */
                qppMethod.setDataSource(String.format("%s/%s.%d.tsv",
                        PreComputedPredictor.qppScoreFilePrefix, qppMethod.name(), sampleId));

                QPPMetricBundle qppMeasuresOnPermutedSamples = evaluateOnSingleSample(cutoff, sampleId);
                if (qppMeasuresOnPermutedSamples == null)
                    continue;

                tau_mean += qppMeasuresOnPermutedSamples.tau;
                delta_tau += Math.abs(qppMeasuresOnInitialRanking.tau - qppMeasuresOnPermutedSamples.tau)/qppMeasuresOnInitialRanking.tau;

                for (int j = 0; j < delta_sare.length; j++) {
                    delta_sare[j] += Math.abs(qppMeasuresOnInitialRanking.perQuerySARE[j] - qppMeasuresOnPermutedSamples.perQuerySARE[j]);
                }
            }

            if (Constants.WRITE_PERMS)
                return null;

            for (int j = 0; j < delta_sare.length; j++)
                delta_sare[j] /= numSamples;

            System.out.println(String.format("%d\t%.4f\t%.4f\t%.4f", cutoff, qppMeasuresOnInitialRanking.tau, tau_mean/numSamples, delta_tau/numSamples));
            bw.write(String.format("%d\t%.4f\t%.4f\t%.4f", cutoff, qppMeasuresOnInitialRanking.tau, tau_mean/numSamples, delta_tau/numSamples));
            bw.newLine();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        QPPMetricBundle averageQppMeasures = new QPPMetricBundle(delta_tau/numSamples, 0, delta_sare);
        return averageQppMeasures;
    }

    public static void main(String[] args) throws Exception {
        //final String resFile = Constants.BM25_Top100_DL1920;
        final String resFile = Constants.ColBERT_Top100_DL1920;

        String[] samplingModes = {
            //"U",
            "R"
        };

        final Metric[] targetMetricNames = {Metric.AP, /*, Metric.nDCG Metric.RR*/};

        for (Metric m: targetMetricNames) {
            for (String samplingMode : samplingModes) {
                StochasticQPPEvaluation stochasticQppEval =
                        new StochasticQPPEvaluation(
                                Constants.QUERIES_DL1920,
                                Constants.QRELS_DL1920,
                                resFile,
                                samplingMode, m);

                QPPMethod[] qppMethods = {
                        //new NQCSpecificity(stochasticQppEval.retriever.getSearcher()),
                        //new CumulativeNQC(stochasticQppEval.retriever.getSearcher()),
                        new PreComputedPredictor("BERT-QPP", stochasticQppEval.topDocsMapWithoutPerturbation, Constants.NQC_CUTOFF)
                        //new RSDSpecificity(new NQCSpecificity(stochasticQppEval.retriever.getSearcher())),
                        //new UEFSpecificity(new NQCSpecificity(stochasticQppEval.retriever.getSearcher()))
                };

                for (QPPMethod qppMethod : qppMethods) {
                    System.out.println(String.format("Evaluating %s on %s with mode=%s", qppMethod.name(), m.toString(), samplingMode));
                    stochasticQppEval.setQppMethod(qppMethod);
                    stochasticQppEval.batchEvaluate();
                }
            }
        }
    }
}
