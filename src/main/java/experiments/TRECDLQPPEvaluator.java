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

public class TRECDLQPPEvaluator {

    static void runExperiment(
            IndexSearcher searcher,
            KNNRelModel knnRelModel,
            Evaluator evaluator,
            List<MsMarcoQuery> queries,
            Map<String, TopDocs> topDocsMap, float lambda, float mu) {
        QPPMethod qppMethod = new KNN_NQCSpecificity(
                new NQCSpecificity(searcher),
                //new UEFSpecificity(new NQCSpecificity(searcher)),
                searcher,
                knnRelModel,
                Constants.QPP_JM_COREL_NUMNEIGHBORS,
                lambda,
                mu
        );

        int numQueries = queries.size();
        double[] qppEstimates = new double[numQueries];
        double[] evaluatedMetricValues = new double[numQueries];

        Metric[] metrics = {Metric.AP};

        for (Metric m: metrics) {
            int i = 0;
            for (MsMarcoQuery query : queries) {
                RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.getId());
                TopDocs topDocs = topDocsMap.get(query.getId());

                evaluatedMetricValues[i] = evaluator.compute(query.getId(), m);
                qppEstimates[i] = (float) qppMethod.computeSpecificity(
                        query, rr, topDocs, Constants.QPP_NUM_TOPK);

                //System.out.println(String.format("%s: AP = %.4f, QPP = %.4f", query.getId(), evaluatedMetricValues[i], qppEstimates[i]));
                i++;
            }
            System.out.println(String.format("Avg. %s: %.4f", m.toString(), Arrays.stream(evaluatedMetricValues).sum()/(double)numQueries));
            double pearsons = new PearsonsCorrelation().correlation(evaluatedMetricValues, qppEstimates);
            double kendals = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);

            System.out.println(String.format("(%.1f, %.1f) -- Target Metric: %s, r = %.4f, tau = %.4f", lambda, mu, m.toString(), pearsons, kendals));
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Required arguments: <res file>");
            args = new String[1];
            args[0] = Constants.RES_FILE;
        }
        final String queryFile = Constants.QUERY_FILE_TEST;
        final String qrelsFile = Constants.QRELS_TEST;
        final String resFile = args[0];

        try {
            OneStepRetriever retriever = new OneStepRetriever(Constants.QUERY_FILE_TEST);
            Settings.init(retriever.getSearcher());

            AllRetrievedResults allRetrievedResults = new AllRetrievedResults(resFile);
            Map<String, TopDocs> topDocsMap = allRetrievedResults.castToTopDocs();

            QPPEvaluator qppEvaluator = new QPPEvaluator(
                    Constants.QUERY_FILE_TEST, Constants.QRELS_TEST,
                    new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
            List<MsMarcoQuery> queries = qppEvaluator.constructQueries(queryFile);

            KNNRelModel knnRelModel = new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TRAIN);
            IndexSearcher searcher = retriever.getSearcher();

            // TODO: Change this to load from a .res file
            //Similarity sim = new LMDirichletSimilarity(1000);
            //Evaluator evaluator = qppEvaluator.executeQueries(queries, sim, Constants.NUM_WANTED, qrelsFile, resFile, topDocsMap);

            Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel

            //System.out.println(topDocsMap.keySet());

            /*
            for (float l=0; l < 1; l+=.2f) {
                for (float m = 0; m < 1; m += .2f) {
                    runExperiment(searcher, knnRelModel, evaluator, queries, topDocsMap, l, m);
                }
            }
            */

            runExperiment(searcher, knnRelModel, evaluator, queries, topDocsMap, 0, 0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
