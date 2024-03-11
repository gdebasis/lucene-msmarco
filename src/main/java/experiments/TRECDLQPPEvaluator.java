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
            String testResFile,
            float maxL, // set this and maxM to 0 for NQC baseline
            float maxM // set this to 0 for JM baseline
    )
    throws Exception {

        IndexSearcher searcher = retriever.getSearcher();
        KNNRelModel knnRelModel = new KNNRelModel(Constants.QRELS_TRAIN, trainQueryFile);

        Evaluator evaluatorTrain = new Evaluator(trainQrelsFile, trainResFile); // load ret and rel
        QPPEvaluator qppEvaluator = new QPPEvaluator(
                trainQueryFile, trainQrelsFile,
                new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
        List<MsMarcoQuery> trainQueries = qppEvaluator.constructQueries(trainQueryFile);

        Map<String, TopDocs> topDocsMap = evaluatorTrain.getAllRetrievedResults().castToTopDocs();

        OptimalHyperParams p = new OptimalHyperParams();

        for (int k=1; k<=3; k++) {
            for (float l = 0; l <= maxL; l += .2f) {
                for (float m = 0; m <= maxM; m += .2f) {
                    double kendals = runExperiment(
                            searcher, knnRelModel, evaluatorTrain,
                            trainQueries, topDocsMap, l, m, k);

                    System.out.println(String.format("Train on %s -- (%.1f, %.1f): tau = %.4f", trainQueryFile, l, m, kendals));
                    if (kendals > p.kendals) {
                        p.l = l;
                        p.m = m;
                        p.k = k;
                    }
                }
            }
        }

        System.out.println(String.format("The best settings: lambda=%.1f, mu=%.1f, k=%d", p.l, p.m, p.k));
        // apply this setting on the test set
        KNNRelModel knnRelModelTest = new KNNRelModel(Constants.QRELS_TRAIN, testQueryFile);

        Evaluator evaluatorTest = new Evaluator(testQrelsFile, testResFile); // load ret and rel
        QPPEvaluator qppEvaluatorTest = new QPPEvaluator(
                testQueryFile, testQrelsFile,
                new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
        List<MsMarcoQuery> testQueries = qppEvaluatorTest.constructQueries(testQueryFile); // these queries are different from train queries

        Map<String, TopDocs> topDocsMapTest = evaluatorTest.getAllRetrievedResults().castToTopDocs();
        double kendals_Test = runExperiment(
                searcher, knnRelModelTest,
                evaluatorTest, testQueries, topDocsMapTest, p.l, p.m, p.k);

        System.out.println(String.format("Kendal's on %s with lambda=%.1f, mu=%.1f, k=%d: %.4f", testQueryFile, p.l, p.m, p.k, kendals_Test));

        return kendals_Test;
    }


    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("Required arguments: <res file DL 19> <res file DL 20> <method (nqc/jm/corel)>");
            args = new String[3];
            args[0] = "ColBERT-PRF-VirtualAppendix/BM25/BM25.2019.res";
            args[1] = "ColBERT-PRF-VirtualAppendix/BM25/BM25.2020.res";
            args[2] = "nqc";
        }

        Metric m = Metric.AP;
        float maxL = 1, maxM = 1;
        if (args[2].equals("nqc")) {
            maxL = 0;
            maxM = 0;
        }
        else if (args[2].equals("jm")) {
            maxL = 1;
            maxM = 0;
        }

        try {
            OneStepRetriever retriever = new OneStepRetriever(Constants.QUERY_FILE_TEST);
            Settings.init(retriever.getSearcher());

            //runSingleExperiment(retriever, Metric.AP);

            double kendalsOnTest = trainAndTest(retriever,
                    QUERY_FILES[0], QRELS_FILES[0],
                    QUERY_FILES[1], QRELS_FILES[1],
                    args[0], args[1], maxL, maxM);
            double kendalsOnTrain = trainAndTest(retriever,
                    QUERY_FILES[1], QRELS_FILES[1],
                    QUERY_FILES[0], QRELS_FILES[0],
                    args[1], args[0], maxL, maxM);

            double kendals = 0.5*(kendalsOnTrain + kendalsOnTest);
            System.out.println(String.format("Target Metric: %s, tau = %.4f", m.toString(), kendals));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    static void runSingleExperiment(OneStepRetriever retriever, Metric m) throws Exception {
        QPPMethod qppMethod = new KNN_NQCSpecificity(
                new NQCSpecificity(retriever.getSearcher()),
                //new UEFSpecificity(new NQCSpecificity(searcher)),
                retriever.getSearcher(),
                new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST),
                Constants.QPP_JM_COREL_NUMNEIGHBORS,
                0.2f,
                0.6f
        );

        Evaluator evaluator = new Evaluator(Constants.QRELS_TEST, "ColBERT-PRF-VirtualAppendix/BM25/BM25.2019.res"); // load ret and rel
        QPPEvaluator qppEvaluator = new QPPEvaluator(
                Constants.QUERY_FILE_TEST, Constants.QRELS_TEST,
                new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
        List<MsMarcoQuery> queries = qppEvaluator.constructQueries(Constants.QUERY_FILE_TEST);
        int numQueries = queries.size();

        Map<String, TopDocs> topDocsMap = evaluator.getAllRetrievedResults().castToTopDocs();

        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];

        int i = 0;
        for (MsMarcoQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.getId());
            TopDocs topDocs = topDocsMap.get(query.getId());

            evaluatedMetricValues[i] = evaluator.compute(query.getId(), m);
            qppEstimates[i] = (float) qppMethod.computeSpecificity(
                    query, rr, topDocs, Constants.QPP_NUM_TOPK);

            System.out.println(String.format("%s: %s = %.4f, QPP = %.4f", query.getId(),
                    m.toString(), evaluatedMetricValues[i], qppEstimates[i]));
            i++;
        }
        double kendals = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        System.out.println(String.format("Target Metric: %s, tau = %.4f", m.toString(), kendals));
    }


}
