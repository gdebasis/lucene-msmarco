package experiments;

import correlation.KendalCorrelation;
import org.apache.lucene.search.TopDocs;
import qpp.*;
import qrels.Evaluator;
import qrels.Metric;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;
import utils.IndexUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleQPPExperimentWorkflow {
    static final int IR_MEASURE_CUTOFF = 10;

    public static void main(String[] args) throws Exception {
        List<MsMarcoQuery> queries;
        final String resFile = Constants.BM25_Top100_DL1920;
        //final String resFile = Constants.ColBERT_Top100_DL1920;

        OneStepRetriever retriever = new OneStepRetriever(Constants.QUERIES_DL1920, resFile, "english");

        DocVectorReader denseVecReader =
                new DocVectorReader(Constants.COLL_DENSEVEC_FILE_CONTRIEVER);
        Map<Integer, float[]> queryVecs = QueryVecLoader.load(Constants.DL1920_CONTRIEVER_VECS);

        QPPMethod[] qppMethods = {
                new NQCSpecificity(retriever.getSearcher(), 50),
//                new VariantSpecificity(
//                        new NQCSpecificity(retriever.getSearcher(), 100),
//                        retriever.getSearcher(),
//                        new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST, false),
//                        5, 0.2f, false, 5
//                ),
//                new OddsRatioSpecificity(retriever.getSearcher(), 0.2f, 50),
//                new WIGSpecificity(retriever.getSearcher(), 20),
//                new NQCCalibratedSpecificity(retriever.getSearcher(), 0.33f, 0.33f, 0.33f, 50),
//                new RSDSpecificity(new NQCSpecificity(retriever.getSearcher(), 100), 50),
//                new UEFSpecificity(new NQCSpecificity(retriever.getSearcher(), 100), 20),
//                new DenseVecSpecificity(denseVecReader, queryVecs, 5),
//                new DenseVecMatryoskaSpecificity(denseVecReader, queryVecs, 3),
//                new SMVSpecificity(retriever.getSearcher(), 10),    // SMV (needs searcher + k)
//                new SMVSpecificity(retriever.getSearcher(), 30),    // SMV (needs searcher + k)
//                new SMVSpecificity(retriever.getSearcher(), 50),    // SMV (needs searcher + k)
//                new SigmaMaxSpecificity(),
//                new SigmaXSpecificity(0.1),                         //SigmaX with threshold (e.g. 0.5)
        };

        queries = retriever.getQueryList();
        IndexUtils.init(retriever.getSearcher());

        Evaluator evaluator = new Evaluator(Constants.QRELS_TEST, resFile, IR_MEASURE_CUTOFF); // Metrics for top-100 (P@10 is still at 10)
        List<Double> evaluatedMetricValues = new ArrayList<>();
        for (MsMarcoQuery query: queries) {
            evaluatedMetricValues.add(evaluator.compute(query.getId(), Metric.P_10));
        }

        for (QPPMethod qppMethod: qppMethods) {
            Map<String, TopDocs> topDocsMap = evaluator.getAllRetrievedResults().castToTopDocs();
            List<Double> qppEstimates = new ArrayList<>();
            for (MsMarcoQuery query : queries) {
                qppEstimates.add(qppMethod.computeSpecificity(query, topDocsMap.get(query.getId())));
            }

            double tau = new KendalCorrelation().correlation(
                    qppEstimates.stream().mapToDouble(Double::doubleValue).toArray(),
                    evaluatedMetricValues.stream().mapToDouble(Double::doubleValue).toArray()
            );

            System.out.println(String.format("model: %s, tau: %.4f", qppMethod.name(), tau));
        }
    }
}
