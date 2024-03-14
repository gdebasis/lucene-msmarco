package experiments;

import correlation.KendalCorrelation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import qpp.NQCSpecificity;
import qpp.QPPMethod;
import qpp.UEFSpecificity;
import qpp.VariantSpecificity;
import qrels.Evaluator;
import qrels.Metric;
import qrels.RetrievedResults;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;

import java.util.List;
import java.util.Map;

public class TRECDLQPPEvaluatorWithGenVariants {

    static final int DL19 = 0;
    static final int DL20 = 1;
    static String[] QUERY_FILES = {"data/trecdl/pass_2019.queries", "data/trecdl/pass_2020.queries"};
    static String[] QRELS_FILES = {"data/trecdl/pass_2019.qrels", "data/trecdl/pass_2020.qrels"};

    static double runExperiment(
            String baseQPPModelName, // nqc/uef
            IndexSearcher searcher,
            KNNRelModel knnRelModel,
            Evaluator evaluator,
            List<MsMarcoQuery> queries,
            Map<String, TopDocs> topDocsMap,
            float lambda, int numVariants, Metric targetMetric) {

        double kendals = 0;

        QPPMethod baseModel = baseQPPModelName.equals("nqc")? new NQCSpecificity(searcher): new UEFSpecificity(new NQCSpecificity(searcher));
        QPPMethod qppMethod = new VariantSpecificity(
                baseModel,
                searcher,
                knnRelModel,
                numVariants,
                lambda
        );

        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];

        int i = 0;
        for (MsMarcoQuery query : queries) {
            RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.getId());
            TopDocs topDocs = topDocsMap.get(query.getId());

            evaluatedMetricValues[i] = evaluator.compute(query.getId(), targetMetric);
            qppEstimates[i] = (float) qppMethod.computeSpecificity(
                    query, rr, topDocs, Constants.QPP_NUM_TOPK);

            //System.out.println(String.format("%s: AP = %.4f, QPP = %.4f", query.getId(), evaluatedMetricValues[i], qppEstimates[i]));
            i++;
        }
        //System.out.println(String.format("Avg. %s: %.4f", targetMetric.toString(), Arrays.stream(evaluatedMetricValues).sum()/(double)numQueries));
        kendals = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);
        return kendals;
    }

    static double trainAndTest(
            String baseModelName,
            OneStepRetriever retriever,
            Metric targetMetric,
            String trainQueryFile,
            String trainQrelsFile,
            String testQueryFile,
            String testQrelsFile,
            String trainResFile,
            String testResFile,
            int maxNumVariants,
            String variantsFile
    )
            throws Exception {
        IndexSearcher searcher = retriever.getSearcher();
        KNNRelModel knnRelModel = new KNNRelModel(Constants.QRELS_TRAIN, trainQueryFile, variantsFile);
        List<MsMarcoQuery> trainQueries = knnRelModel.getQueries();

        Evaluator evaluatorTrain = new Evaluator(trainQrelsFile, trainResFile); // load ret and rel
        Map<String, TopDocs> topDocsMap = evaluatorTrain.getAllRetrievedResults().castToTopDocs();

        OptimalHyperParams p = new OptimalHyperParams();

        for (int numVariants=1; numVariants<=maxNumVariants; numVariants++) {
            for (float l = 0; l <= 1; l += Constants.QPP_COREL_LAMBDA_STEPS) {
                double kendals = runExperiment(baseModelName,
                        searcher, knnRelModel, evaluatorTrain,
                        trainQueries, topDocsMap, l, numVariants, targetMetric);

                System.out.println(String.format("Train on %s -- (%.1f, %d): tau = %.4f",
                        trainQueryFile, l, numVariants, kendals));
                if (kendals > p.kendals) {
                    p.l = l;
                    p.numVariants = numVariants;
                    p.kendals = kendals; // keep track of max
                }
            }
        }
        System.out.println(String.format("The best settings: lambda=%.1f, nv=%d", p.l, p.numVariants));
        // apply this setting on the test set
        KNNRelModel knnRelModelTest = new KNNRelModel(Constants.QRELS_TRAIN, testQueryFile, variantsFile);
        List<MsMarcoQuery> testQueries = knnRelModelTest.getQueries(); // these queries are different from train queries

        Evaluator evaluatorTest = new Evaluator(testQrelsFile, testResFile); // load ret and rel

        Map<String, TopDocs> topDocsMapTest = evaluatorTest.getAllRetrievedResults().castToTopDocs();
        double kendals_Test = runExperiment(baseModelName,
                searcher, knnRelModelTest,
                evaluatorTest, testQueries, topDocsMapTest, p.l, p.numVariants, targetMetric);

        System.out.println(String.format(
                "Kendal's on %s with lambda=%.1f, M=%d: %.4f",
                testQueryFile, p.l, p.numVariants, kendals_Test));

        return kendals_Test;
    }

    static double trainAndTest(
            String baseModelName,
            OneStepRetriever retriever,
            Metric targetMetric,
            String trainQueryFile,
            String trainQrelsFile,
            String testQueryFile,
            String testQrelsFile,
            String trainResFile,
            String testResFile,
            int maxNumVariants,
            int maxNumNeighbors
    )
            throws Exception {

        IndexSearcher searcher = retriever.getSearcher();
        KNNRelModel knnRelModel = new KNNRelModel(Constants.QRELS_TRAIN, trainQueryFile);

        Evaluator evaluatorTrain = new Evaluator(trainQrelsFile, trainResFile); // load ret and rel
        List<MsMarcoQuery> trainQueries = knnRelModel.getQueries();

        Map<String, TopDocs> topDocsMap = evaluatorTrain.getAllRetrievedResults().castToTopDocs();

        OptimalHyperParams p = new OptimalHyperParams();

        for (int numVariants=1; numVariants<=maxNumVariants; numVariants++) {
            for (int numNeighbors = 1; numNeighbors <= maxNumNeighbors; numNeighbors++) {
                for (float l = 0; l <= 1; l += .2f) {
                    for (float m = 0; m <= 1; m += .2f) {
                        double kendals = runExperiment(baseModelName,
                                searcher, knnRelModel, evaluatorTrain,
                                trainQueries, topDocsMap, l, numVariants, targetMetric);

                        System.out.println(String.format("Train on %s -- (%.1f, %d): tau = %.4f",
                                trainQueryFile, l, numVariants, kendals));
                        if (kendals > p.kendals) {
                            p.l = l;
                            p.m = m;
                            p.numNeighbors = numNeighbors;
                            p.numVariants = numVariants;
                            p.kendals = kendals; // keep track of max
                        }
                    }
                }
            }
        }
        System.out.println(String.format("The best settings: lambda=%.1f, mu=%.1f, nv=%d nn=%d", p.l, p.m, p.numVariants, p.numNeighbors));
        // apply this setting on the test set
        KNNRelModel knnRelModelTest = new KNNRelModel(Constants.QRELS_TRAIN, testQueryFile);

        Evaluator evaluatorTest = new Evaluator(testQrelsFile, testResFile); // load ret and rel
        QPPEvaluator qppEvaluatorTest = new QPPEvaluator(
                testQueryFile, testQrelsFile,
                new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
        List<MsMarcoQuery> testQueries = qppEvaluatorTest.constructQueries(testQueryFile); // these queries are different from train queries

        Map<String, TopDocs> topDocsMapTest = evaluatorTest.getAllRetrievedResults().castToTopDocs();
        double kendals_Test = runExperiment(baseModelName,
                searcher, knnRelModelTest,
                evaluatorTest, testQueries, topDocsMapTest, p.l, p.numVariants, targetMetric);

        System.out.println(String.format(
                "Kendal's on %s with lambda=%.1f, mu=%.1f, M=%d, N=%d: %.4f",
                testQueryFile, p.l, p.m, p.numVariants, p.numNeighbors, kendals_Test));

        return kendals_Test;
    }

    static void runSingleExperiment(
            String baseModelName,
            OneStepRetriever retriever,
            String queryFile, String qrelsFile,
            String resFile,
            Metric targetMetric,
            int numVariants,
            float l,
            String variantFile
    )
            throws Exception {

        KNNRelModel knnRelModel = new KNNRelModel(Constants.QRELS_TRAIN, queryFile, variantFile);
        List<MsMarcoQuery> testQueries = knnRelModel.getQueries(); // these queries are different from train queries

        Evaluator evaluatorTest = new Evaluator(qrelsFile, resFile); // load ret and rel

        Map<String, TopDocs> topDocsMapTest = evaluatorTest.getAllRetrievedResults().castToTopDocs();
        double kendals = runExperiment(baseModelName, retriever.getSearcher(),
                knnRelModel, evaluatorTest, testQueries, topDocsMapTest,
                l, numVariants, targetMetric);
        System.out.println(String.format("Target Metric: %s, tau = %.4f", targetMetric.toString(), kendals));
    }

    public static void main(String[] args) {

        if (args.length < 5) {
            System.out.println("Required arguments: <res file DL 19> <res file DL 20> <metric (ap/ndcg)> <uef/nqc> <rlm/w2v (variant gen)>");
            args = new String[5];
            args[0] = "runs/splade.dl19.100.pp";
            args[1] = "runs/splade.dl20.100.pp";
            args[2] = "ap";
            args[3] = "nqc";
            args[4] = "rlm";
        }

        Metric targetMetric = args[2].equals("ap")? Metric.AP : Metric.nDCG;
        String variantFile = args[4].equals("rlm")? Constants.QPP_JM_VARIANTS_FILE_RLM: Constants.QPP_JM_VARIANTS_FILE_W2V;

        try {
            OneStepRetriever retriever = new OneStepRetriever(Constants.QUERY_FILE_TEST);
            Settings.init(retriever.getSearcher());

            /*
            for (int i=0; i<=1; i++) {
                runSingleExperiment(args[3], retriever, QUERY_FILES[i], QRELS_FILES[i], args[i], targetMetric, 3, 0.5f, variantFile);
            }
            System.exit(0);
            */

            double kendalsOnTest = trainAndTest(args[3], retriever, targetMetric,
                    QUERY_FILES[DL19], QRELS_FILES[DL19],
                    QUERY_FILES[DL20], QRELS_FILES[DL20],
                    args[0], args[1], Constants.QPP_COREL_MAX_VARIANTS, variantFile);
            double kendalsOnTrain = trainAndTest(args[3], retriever, targetMetric,
                    QUERY_FILES[DL20], QRELS_FILES[DL20],
                    QUERY_FILES[DL19], QRELS_FILES[DL19],
                    args[1], args[0], Constants.QPP_COREL_MAX_VARIANTS, variantFile);

            double kendals = 0.5*(kendalsOnTrain + kendalsOnTest);
            System.out.println(String.format("Target Metric: %s, tau = %.4f", targetMetric.toString(), kendals));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
