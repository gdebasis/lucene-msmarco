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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleQppEvalFlow {

    public static void main(String[] args) {
        final String queryFile = Constants.QUERY_FILE_TEST;
        final String resFile = Constants.RES_FILE;
        final String qrelsFile = Constants.QRELS_TEST;

        try {

            OneStepRetriever retriever = new OneStepRetriever(Constants.QUERY_FILE_TEST);
            Settings.init(retriever.getSearcher());

            QPPEvaluator qppEvaluator = new QPPEvaluator(
                    Constants.QUERY_FILE_TEST, Constants.QRELS_TEST,
                    new KendalCorrelation(), retriever.getSearcher(), Constants.QPP_NUM_TOPK);
            List<MsMarcoQuery> queries = qppEvaluator.constructQueries(queryFile);

            KNNRelModel knnRelModel = new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TRAIN);
            IndexSearcher searcher = retriever.getSearcher();

            QPPMethod qppMethod = new KNN_NQCSpecificity(
                    new NQCSpecificity(searcher),
                    searcher,
                    knnRelModel,
                    Constants.QPP_JM_COREL_NUMNEIGHBORS,
                    Constants.QPP_JM_COREL_LAMBDA,
                    Constants.QPP_JM_COREL_MU
            );

            Similarity sim = new LMDirichletSimilarity(1000);

            Map<String, TopDocs> topDocsMap = new HashMap<>();
            Evaluator evaluator = qppEvaluator.executeQueries(queries, sim, Constants.NUM_WANTED, qrelsFile, resFile, topDocsMap);
            System.out.println(topDocsMap.keySet());

            int numQueries = queries.size();
            double[] qppEstimates = new double[numQueries];
            double[] evaluatedMetricValues = new double[numQueries];

            int i = 0;
            for (MsMarcoQuery query : queries) {
                RetrievedResults rr = evaluator.getRetrievedResultsForQueryId(query.getId());
                TopDocs topDocs = topDocsMap.get(query.getId());

                evaluatedMetricValues[i] = evaluator.compute(query.getId(), Metric.AP);
                qppEstimates[i] = (float)qppMethod.computeSpecificity(
                        query, rr, topDocs, Constants.QPP_NUM_TOPK);

                System.out.println(String.format("%s: AP = %.4f, QPP = %.4f", query.getId(), evaluatedMetricValues[i], qppEstimates[i]));
                i++;
            }
            double pearsons = new PearsonsCorrelation().correlation(evaluatedMetricValues, qppEstimates);
            double kendals = new KendalCorrelation().correlation(evaluatedMetricValues, qppEstimates);

            System.out.println(String.format("r = %.4f, tau = %.4f", pearsons, kendals));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}