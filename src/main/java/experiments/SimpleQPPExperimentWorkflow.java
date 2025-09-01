package experiments;

import correlation.KendalCorrelation;
import org.apache.lucene.search.TopDocs;
import qpp.*;
import qrels.Evaluator;
import qrels.Metric;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;
import stochastic_qpp.QPPMetricBundle;
import utils.IndexUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleQPPExperimentWorkflow {
    static final int NUM_DOCS = 50;

    public static void main(String[] args) throws Exception {
        List<MsMarcoQuery> queries;
        final String resFile = "data/runs/1920/dense_qpp_another_calculation/colbert.e2e.100.res";
//                "data/runs/1920/trecdl1920.colbert-e2e.100.res";
        OneStepRetriever retriever = new OneStepRetriever(Constants.QUERY_FILE_TEST, resFile, "english");

//        DocVectorReader denseVecReader =
//                new DocVectorReader(Constants.COLL_DENSEVEC_FILE_mnli);
//        Map<Integer, float[]> queryVecs = QueryVecLoader.load(Constants.DL1920_mnli_VECS);

        QPPMethod[] qppMethods = {
//                new NQCSpecificity(retriever.getSearcher(), 100),
//                new VariantSpecificity(
//                        new NQCSpecificity(retriever.getSearcher(), 100),
//                        retriever.getSearcher(),
//                        new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST, false),
//                        5, 0.2f, false, 5
//                ),
//                new OddsRatioSpecificity(retriever.getSearcher(), 0.2f, 50),
//                new WIGSpecificity(retriever.getSearcher(), 5),
//                new NQCCalibratedSpecificity(retriever.getSearcher(), 0.33f, 0.33f, 0.33f, 50),
//                new RSDSpecificity(new NQCSpecificity(retriever.getSearcher(), 100), 50),
//                new UEFSpecificity(new NQCSpecificity(retriever.getSearcher(), 100), 20),
//                new DenseVecSpecificity(denseVecReader, queryVecs, 30),
//                new DenseVecMatryoskaSpecificity(denseVecReader, queryVecs, 3),
//                new SMVSpecificity(retriever.getSearcher(), 100),    // SMV (needs searcher + k)
//                new SigmaMaxSpecificity(),
//                new SigmaXSpecificity(0.1),                         //SigmaX with threshold (e.g. 0.5)
        };

        queries = retriever.getQueryList();
        IndexUtils.init(retriever.getSearcher());

        Evaluator evaluator = new Evaluator(Constants.QRELS_TEST, resFile, NUM_DOCS); // Metrics for top-100 (P@10 is still at 10)
        List<Double> evaluatedMetricValues = new ArrayList<>();
        for (MsMarcoQuery query: queries) {
            evaluatedMetricValues.add(evaluator.compute(query.getId(), Metric.AP));
        }

        for (QPPMethod qppMethod: qppMethods) {
            Map<String, TopDocs> topDocsMap = evaluator.getAllRetrievedResults().castToTopDocs();
            List<Double> qppEstimates = new ArrayList<>();
            for (MsMarcoQuery query : queries) {
                qppEstimates.add(qppMethod.computeSpecificity(query, topDocsMap.get(query.getId())));
//                qppEstimates.add(qppMethod.computeSpecificity(query, topDocsMap.get(query.getId()), 50));
            }

            double tau = new KendalCorrelation().correlation(
                    qppEstimates.stream().mapToDouble(Double::doubleValue).toArray(),
                    evaluatedMetricValues.stream().mapToDouble(Double::doubleValue).toArray()
            );

            System.out.println(String.format("model: %s, tau: %.4f", qppMethod.name(), tau));
        }
    }
}
