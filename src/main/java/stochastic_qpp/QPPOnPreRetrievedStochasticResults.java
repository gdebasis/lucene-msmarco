package stochastic_qpp;

import correlation.KendalCorrelation;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.checkerframework.checker.units.qual.C;
import qpp.*;
import qrels.Evaluator;
import qrels.Metric;
import qrels.PreEvaluatedResults;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import utils.IndexUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class QPPOnPreRetrievedStochasticResults {
    PreRetrievedStochasticResults preRetrievedResults;
    PreEvaluatedResults preEvaluatedResults;

    final static int NUM_RANKINGS = 10;

    static Map<String, Float> inducedDocScoreCache = new HashMap<>();

    public QPPOnPreRetrievedStochasticResults(
        IndexReader reader, String queryFile, String qrelsFile,
        String resFile) throws Exception {

        preRetrievedResults = new PreRetrievedStochasticResults(
                reader, resFile, queryFile,
                inducedDocScoreCache, NUM_RANKINGS, qrelsFile);
        preEvaluatedResults = new PreEvaluatedResults(resFile + ".eval");
    }

    public double evaluate(QPPMethod qppMethod, Metric targetMetric) {
        int numQueries = preRetrievedResults.queries.keySet().size();
        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];
        double avgInitialTau = 0;

        double tau_on_pivot, avg_tau_on_permutation_samples = 0, del_tau = 0, avg_del_tau = 0;

        for (int i=0; i < NUM_RANKINGS; i++) {
            Map<String, TopDocs> topDocsMap = this.preRetrievedResults.topDocsMaps[i];
            Evaluator evaluator = this.preRetrievedResults.permutationEvaluators[i];

            int query_index = 0;
            for (MsMarcoQuery query: preRetrievedResults.queries.values()) {
                TopDocs topDocs = topDocsMap.get(query.getId());
                qppEstimates[query_index] = qppMethod.computeSpecificity(query, topDocs);
                evaluatedMetricValues[query_index] = targetMetric==Metric.AWRF?
                        preEvaluatedResults.compute(query.getId(), Metric.AWRF):
                        evaluator.compute(query.getId(), targetMetric);
                query_index++;
            }

            /*
            for (double m: evaluatedMetricValues) {
                System.out.print(String.format("%.4f, ", m));
            }
            System.out.println();
            */

            tau_on_pivot = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
            avgInitialTau += tau_on_pivot;
            if (false) {
                avg_tau_on_permutation_samples = evaluateWRTPivot(i, qppMethod, targetMetric); // eval relative to other
                del_tau = Math.abs(tau_on_pivot - avg_tau_on_permutation_samples); // /tau_on_pivot;

                System.out.println(
                    String.format("initial tau (%s) = %.4f, avg tau over other rankings = %.4f, del = %.4f",
                            targetMetric.toString(), tau_on_pivot, avg_tau_on_permutation_samples, del_tau));
                avg_del_tau += del_tau;
            }
        }
        System.out.println(String.format("Avg Initial tau = %.4f", avgInitialTau/NUM_RANKINGS));

        return avg_del_tau/NUM_RANKINGS;
    }

    public double evaluateWRTPivot(int pivotIndex, QPPMethod qppMethod, Metric targetMetric) {
        int numQueries = preRetrievedResults.queries.keySet().size();
        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];
        double avg_tau_over_permutation_samples = 0;

        for (int i=0; i < NUM_RANKINGS; i++) {
            if (i==pivotIndex)
                continue;

            Map<String, TopDocs> topDocsMap = this.preRetrievedResults.topDocsMaps[i];
            Evaluator permutationEvaluator = this.preRetrievedResults.permutationEvaluators[i];

            int query_index = 0;
            for (MsMarcoQuery query: preRetrievedResults.queries.values()) {
                String qid = query.getId();
                TopDocs permutedSample = topDocsMap.get(qid);
                qppEstimates[query_index] = qppMethod.computeSpecificity(query, permutedSample);
                evaluatedMetricValues[query_index] =
                        evaluatedMetricValues[query_index] = targetMetric==Metric.AWRF?
                                preEvaluatedResults.compute(query.getId(), Metric.AWRF):
                                permutationEvaluator.compute(query.getId(), targetMetric);
                query_index++;
            }

            avg_tau_over_permutation_samples += new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        }
        return avg_tau_over_permutation_samples/(NUM_RANKINGS-1);
    }

    public static void main(String[] args) {
        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Constants.TREC_FAIR_IR_INDEX).toPath()));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            IndexUtils.init(searcher);

            final QPPMethod[] qppMethods = {
                    new NQCSpecificity(searcher, Constants.NQC_CUTOFF),
                    new CumulativeNQC(searcher, Constants.NQC_CUTOFF),
                    new RSDSpecificity(new NQCSpecificity(searcher, Constants.NQC_CUTOFF)),
                    new UEFSpecificity(new NQCSpecificity(searcher, Constants.NQC_CUTOFF))
            };

            String[] resFiles = {
                    Constants.TREC_FAIR_IR_STOCHASTIC_RUNS_DIR + "/input.UoGTrMabWeSA",
                    Constants.TREC_FAIR_IR_STOCHASTIC_RUNS_DIR + "/input.UoGTrMabSaWR"
            };

            for (String resFile: resFiles) {
                QPPOnPreRetrievedStochasticResults qppOnPreRetrievedResults =
                        new QPPOnPreRetrievedStochasticResults(
                            reader,
                            Constants.TREC_FAIR_IR_QUERY_FILE,
                            Constants.TREC_FAIR_IR_QRELS_FILE,
                            resFile
                        );

                for (QPPMethod qppMethod : qppMethods) { // NQC, CNQC etc.
                    double corr = qppOnPreRetrievedResults.evaluate(qppMethod, Metric.AWRF);
                    System.out.println(String.format("tau (%s, %s) = %.4f",
                            qppMethod.name(),
                            resFile,
                            corr)
                    );
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
