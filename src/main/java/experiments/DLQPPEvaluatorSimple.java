package experiments;

import org.apache.lucene.search.TopDocs;
import qpp.*;
import qrels.Evaluator;
import qrels.Metric;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;
import stochastic_qpp.TauAndSARE;
import utils.IndexUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.*;
import java.util.*;
import java.util.function.*;

public class DLQPPEvaluatorSimple {

    static <T>Map<T, Long> frequencyMap(Stream<T> elements) {
        return elements.collect(
                Collectors.groupingBy(
                        Function.identity(),
                        HashMap::new, // can be skipped
                        Collectors.counting()
                )
        );
    }

    static TauAndSARE evaluate(List<MsMarcoQuery> queries,
                         QPPMethod qppMethod,
                         Metric targetMetric,
                         Evaluator evaluator) {
        int i;
        Map<String, TopDocs> topDocsMap = evaluator.getAllRetrievedResults().castToTopDocs();

        double[] qppEstimates = new double[queries.size()];
        double[] evaluatedMetricValues = new double[queries.size()];

        i = 0;
        for (MsMarcoQuery query: queries) {
            evaluatedMetricValues[i] = evaluator.compute(query.getId(), targetMetric);
            qppEstimates[i] = qppMethod.computeSpecificity(query, topDocsMap.get(query.getId()), 50);
            System.out.println(String.format(
                    "%s = %.4f, %s = %.4f",
                    qppMethod.name(), qppEstimates[i], targetMetric.name(), evaluatedMetricValues[i]));
            i++;
        }

        System.out.println(frequencyMap(Arrays.stream(evaluatedMetricValues).boxed()));

        return new TauAndSARE(evaluatedMetricValues, qppEstimates);
    }

    public static void main(String[] args) throws Exception {
        List<MsMarcoQuery> queries;
        final String resFile = /* Constants.BM25_Top100_DL1920 */ Constants.ColBERT_Top100_DL1920;
        final Metric[] targetMetricNames = {Metric.RR};

        OneStepRetriever retriever = new OneStepRetriever(Constants.QUERIES_DL1920, resFile);

        QPPMethod[] qppMethods = {
                new NQCSpecificity(retriever.getSearcher()),
                //new RSDSpecificity(new NQCSpecificity(retriever.getSearcher())),
                //new UEFSpecificity(new NQCSpecificity(retriever.getSearcher()))
        };

        queries = retriever.getQueryList();
        IndexUtils.init(retriever.getSearcher());

        Evaluator evaluator = new Evaluator(Constants.QRELS_DL1920, resFile, 10); // Metrics for top-10

        for (QPPMethod qppMethod: qppMethods) {
            for (Metric targetMetric: targetMetricNames) {
                TauAndSARE qppMetrics = evaluate(queries, qppMethod, targetMetric, evaluator);
                System.out.println(String.format("%s on %s: tau = %.4f, sare = %.4f",
                        qppMethod.name(),
                        targetMetric.name(),
                        qppMetrics.tau(), qppMetrics.sare()));
            }
        }
    }
}
