package experiments;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import correlation.KendalCorrelation;
import qrels.Evaluator;
import qrels.Metric;
import qrels.*;
import qpp.*;

import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class OptimalHyperParams {
    float l;
    float m;
    int k;
    double kendals;
}

public class TRECDLQPPEvaluator {
    static float l, m;
    static int k;
    static String[] QUERY_FILES = {"data/trecdl/pass_2019.queries", "data/trecdl/pass_2020.queries"};
    static String[] QRELS_FILES = {"data/trecdl/pass_2019.qrels", "data/trecdl/pass_2020.qrels"};

    static double runExperiment(
            IndexSearcher searcher,
            KNNRelModel knnRelModel,
            Evaluator evaluator,
            List<MsMarcoQuery> queries,
            Map<String, TopDocs> topDocsMap, float lambda, float mu, int numNeighbors) {

        double kendals = 0;

        QPPMethod qppMethod = new KNN_NQCSpecificity(
                new NQCSpecificity(searcher),
                //new UEFSpecificity(new NQCSpecificity(searcher)),
                searcher,
                knnRelModel,
                numNeighbors,
                lambda,
                mu
        );

        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];

        int i = 0;
        for (MsMarcoQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.getId());
            TopDocs topDocs = topDocsMap.get(query.getId());

            evaluatedMetricValues[i] = evaluator.compute(query.getId(), Metric.AP);
            qppEstimates[i] = (float) qppMethod.computeSpecificity(
                    query, rr, topDocs, Constants.QPP_NUM_TOPK);

            //System.out.println(String.format("%s: AP = %.4f, QPP = %.4f", query.getId(), evaluatedMetricValues[i], qppEstimates[i]));
            i++;
        }
        //System.out.println(String.format("Avg. %s: %.4f", m.toString(), Arrays.stream(evaluatedMetricValues).sum()/(double)numQueries));
        kendals = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);

        // System.out.println(String.format("(%.1f, %.1f) -- Target Metric: %s, r = %.4f, tau = %.4f", lambda, mu, m.toString(), pearsons, kendals));
        return kendals;
    }

    static double trainAndTest(
            OneStepRetriever retriever,
            String trainQueryFile,
            String trainQrelsFile,
            String testQueryFile,
            String testQrelsFile,
            String trainResFile,
            String testResFile) throws Exception {

        IndexSearcher searcher = retriever.getSearcher();
        KNNRelModel knnRelModel = new KNNRelModel(trainQrelsFile, trainQueryFile);

        Evaluator evaluatorTrain = new Evaluator(trainQrelsFile, trainResFile); // load ret and rel
        QPPEvaluator qppEvaluator = new QPPEvaluator(
                trainQueryFile, trainQrelsFile,
                new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
        List<MsMarcoQuery> trainQueries = qppEvaluator.constructQueries(trainQueryFile);

        AllRetrievedResults allRetrievedResults = new AllRetrievedResults(trainResFile);
        Map<String, TopDocs> topDocsMap = allRetrievedResults.castToTopDocs();

        OptimalHyperParams p = new OptimalHyperParams();

        for (int k=1; k<=3; k++) {
            for (float l = 0; l < 1; l += .2f) {
                for (float m = 0; m < 1; m += .2f) {
                    double kendals = runExperiment(
                            searcher, knnRelModel, evaluatorTrain,
                            trainQueries, topDocsMap, l, m, k);

                    System.out.println(String.format("(%.1f, %.1f): tau = %.4f", l, m, kendals));
                    if (kendals > p.kendals) {
                        p.l = l;
                        p.m = m;
                        p.k = k;
                    }
                }
            }
        }

        // apply this setting on the test set
        KNNRelModel knnRelModelTest = new KNNRelModel(trainQrelsFile, trainQueryFile);

        Evaluator evaluatorTest = new Evaluator(testQrelsFile, testResFile); // load ret and rel
        QPPEvaluator qppEvaluatorTest = new QPPEvaluator(
                testQueryFile, testQrelsFile,
                new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
        List<MsMarcoQuery> testQueries = qppEvaluator.constructQueries(testQueryFile);

        AllRetrievedResults allRetrievedResultsTest = new AllRetrievedResults(testResFile);
        Map<String, TopDocs> topDocsMapTest = allRetrievedResultsTest.castToTopDocs();
        double kendals_Test = runExperiment(
                searcher, knnRelModelTest,
                evaluatorTest, testQueries, topDocsMapTest, p.l, p.m, p.k);

        return kendals_Test;
    }


    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Required arguments: <res file DL 19> <res file DL 20>");
            args = new String[2];
            args[0] = "ColBERT-PRF-VirtualAppendix/BM25/BM25.2019.res";
            args[1] = "ColBERT-PRF-VirtualAppendix/BM25/BM25.2020.res";
        }

        Metric m = Metric.AP;

        try {
            OneStepRetriever retriever = new OneStepRetriever(Constants.QUERY_FILE_TEST);
            Settings.init(retriever.getSearcher());

            double kendalsOnTest = trainAndTest(retriever,
                    QUERY_FILES[0], QRELS_FILES[0],
                    QUERY_FILES[1], QRELS_FILES[1],
                    args[0], args[1]);
            double kendalsOnTrain = trainAndTest(retriever,
                    QUERY_FILES[1], QRELS_FILES[1],
                    QUERY_FILES[0], QRELS_FILES[0],
                    args[1], args[0]);

            double kendals = 0.5*(kendalsOnTrain + kendalsOnTest);

            System.out.println(String.format("Target Metric: %s, tau = %.4f", m.toString(), kendals));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
