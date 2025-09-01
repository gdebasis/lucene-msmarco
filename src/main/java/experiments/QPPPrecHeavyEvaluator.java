package experiments;

import correlation.NDCGCorrelation;
import correlation.QPPCorrelationMetric;
import org.apache.commons.math3.stat.correlation.KendallsCorrelation;
import org.apache.lucene.search.TopDocs;
import qpp.*;
import qrels.*;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;
import stochastic_qpp.QPPMetricBundle;
import utils.IndexUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.*;
import java.util.*;
import java.util.function.*;

public class QPPPrecHeavyEvaluator {
    static double DELTA = 0.05;
    static final int NUM_RANKINGS = 500;
    static final int NUM_DOCS = 50;

    enum QPPCorrelationMode {
        TAU, NDCG
    }

    static <T>Map<T, Long> frequencyMap(Stream<T> elements) {
        return elements.collect(
                Collectors.groupingBy(
                        Function.identity(),
                        HashMap::new, // can be skipped
                        Collectors.counting()
                )
        );
    }

    static QPPMetricBundle evaluate(List<MsMarcoQuery> queries,
                                    QPPMethod qppMethod,
                                    Evaluator evaluator,
                                    double[] evaluatedMetricValues,
                                    EvalMetricTieBreaker tieBreaker) throws IOException {
        int i;
        Map<String, TopDocs> topDocsMap = evaluator.getAllRetrievedResults().castToTopDocs();
        double[][] evaluatedMetricMatrix = tieBreaker.transform(evaluatedMetricValues);

        double[] qppEstimates = new double[queries.size()];

        i = 0;
        for (MsMarcoQuery query: queries) {
            qppEstimates[i++] = qppMethod.computeSpecificity(query, topDocsMap.get(query.getId()));
        }

        List<QPPMetricBundle> qPPMetricBundleList = new ArrayList<>();
        for (i=0; i < evaluatedMetricMatrix.length; i++) {
            QPPMetricBundle qpMetricBundle = new QPPMetricBundle(evaluatedMetricMatrix[i], qppEstimates);
            qPPMetricBundleList.add(qpMetricBundle);
        }

        BufferedWriter bw = new BufferedWriter(new FileWriter(
                String.format("%s.%s.tsv", qppMethod.name(), tieBreaker.name())));

        for (QPPMetricBundle qppMetricBundle: qPPMetricBundleList) {
            bw.write(String.format("%.4f\t%.4f", qppMetricBundle.tau(), qppMetricBundle.ndcg()));
            bw.newLine();
        }
        bw.close();

        double tau_mean = qPPMetricBundleList.stream()
               .map(x -> x.tau())
               .mapToDouble(Double::doubleValue)
               .average()
               .orElse(0.0);
        double ndcg_mean = qPPMetricBundleList.stream()
                .map(x -> x.ndcg())
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        List<double[]> perQuerySAREValuesList =
                qPPMetricBundleList.stream().map(x->x.getPerQuerySARE()).collect(Collectors.toList());
        double[] mean_sare = new double[perQuerySAREValuesList.get(0).length];
        for (double[] perQuerySAREValues: perQuerySAREValuesList) {
            for (int j=0; j < perQuerySAREValues.length; j++) {
                mean_sare[j] += perQuerySAREValues[j];
            }
            mean_sare = Arrays.stream(mean_sare).map(x->x/perQuerySAREValues.length).toArray();
        }

        return new QPPMetricBundle(tau_mean, ndcg_mean, mean_sare);
    }

    static QPPMetricBundle evaluate(List<MsMarcoQuery> queries,
                                    QPPMethod qppMethod,
                                    Evaluator evaluator, double[] evaluatedMetricValues) {
        int i;
        Map<String, TopDocs> topDocsMap = evaluator.getAllRetrievedResults().castToTopDocs();

        double[] qppEstimates = new double[queries.size()];

        i = 0;
        for (MsMarcoQuery query: queries) {
            qppEstimates[i] = qppMethod.computeSpecificity(query, topDocsMap.get(query.getId()));
            /*
            System.out.println(String.format(
                    "%s = %.4f, %s = %.4f",
                    qppMethod.name(), qppEstimates[i], targetMetric.name(), evaluatedMetricValues[i]));
             */
            i++;
        }
        return new QPPMetricBundle(evaluatedMetricValues, qppEstimates);
    }

    public static double[] runExperiment(
            EvalMetricTieBreaker evalMetricTieBreaker,
            Metric targetMetric, QPPCorrelationMode mode)
    throws Exception {

        List<MsMarcoQuery> queries;
        final String resFile =
                //Constants.BM25_Top100_DL1920
                "pass_2019.queries.res"
                //Constants.ColBERT_Top100_DL1920
                ;
        OneStepRetriever retriever = new OneStepRetriever(Constants.QUERY_FILE_TEST, resFile, "english");

        QPPMethod[] qppMethods = {
                new NQCSpecificity(retriever.getSearcher(), 50),
                new VariantSpecificity(
                        new NQCSpecificity(retriever.getSearcher(), 50),
                        retriever.getSearcher(),
                        new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST, false),
                        5, 0.5f, false, 50
                ),
                new VariantSpecificity(
                        new NQCSpecificity(retriever.getSearcher(), 50),
                        retriever.getSearcher(),
                        new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST, false),
                        5, 0.2f, false, 50
                ),
                new VariantSpecificity(
                        new NQCSpecificity(retriever.getSearcher(), 50),
                        retriever.getSearcher(),
                        new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST, false),
                        5, 0.8f,false,  50
                ),
                new OddsRatioSpecificity(retriever.getSearcher(), 0.4f, 50),
                new WIGSpecificity(retriever.getSearcher(), 50),
                new NQCCalibratedSpecificity(retriever.getSearcher(), 0.33f, 0.33f, 0.33f, 50),
                new NQCCalibratedSpecificity(retriever.getSearcher(), 0.8f, 0.1f, 0.1f, 50),
                new NQCCalibratedSpecificity(retriever.getSearcher(), 0.1f, 0.8f, 0.1f, 50),
                new NQCCalibratedSpecificity(retriever.getSearcher(), 0.1f, 0.1f, 0.8f, 50),
                new RSDSpecificity(new NQCSpecificity(retriever.getSearcher(), 50)),
                new UEFSpecificity(new NQCSpecificity(retriever.getSearcher(), 50))
        };

        queries = retriever.getQueryList();
        IndexUtils.init(retriever.getSearcher());

        Evaluator evaluator = new Evaluator(Constants.QRELS_TEST, resFile, NUM_DOCS); // Metrics for top-100 (P@10 is still at 10)
        List<QPPMethod> evaluatedQPPModels = new ArrayList<>();

        double[] evaluatedMetricValues = new double[queries.size()];
        int i=0;
        for (MsMarcoQuery query: queries) {
            evaluatedMetricValues[i++] = evaluator.compute(query.getId(), targetMetric);
        }
        //System.out.println(frequencyMap(Arrays.stream(evaluatedMetricValues).boxed()));

        for (QPPMethod qppMethod: qppMethods) {
            QPPMetricBundle qppMetrics = evaluate(
                    queries, qppMethod,
                    evaluator, evaluatedMetricValues,
                    evalMetricTieBreaker
            );
            System.out.println(String.format("%s on %s: tau = %.4f, nDCG = %.4f",
                    qppMethod.name(),
                    targetMetric.name(),
                    qppMetrics.tau(), qppMetrics.ndcg()));

            qppMethod.setMeasure(qppMetrics);
            evaluatedQPPModels.add(qppMethod);
        }

//        List<QPPMethod> modelsRankedByPerfMeasure =
//            evaluatedQPPModels.stream().sorted(
//                (o1, o2)->
//                Double.compare(o1.getMeasure().tau(), o2.getMeasure().tau())
//            )
//            .collect(Collectors.toList());

//        for (QPPMethod qppModel: modelsRankedByPerfMeasure) {
//            System.out.println(String.format("%s: %.4f", qppModel.name(), qppModel.getMeasure().tau()));
//        }

        return mode==QPPCorrelationMode.TAU?
            evaluatedQPPModels.stream()
                .mapToDouble(x -> x.getMeasure().tau())
                .toArray():
            evaluatedQPPModels.stream()
                    .mapToDouble(x -> x.getMeasure().ndcg())
                    .toArray()
        ;
    }

    public static double findCorrelation(EvalMetricTieBreaker evalMetricTieBreaker,
         Metric measureWithLikelyTies, Metric refMeasure, QPPCorrelationMode mode) throws Exception {
        double[] runA = runExperiment(evalMetricTieBreaker, measureWithLikelyTies, mode);
        double[] runB = runExperiment(new NoTieBreaker(), refMeasure, mode);

        return (new KendallsCorrelation()).correlation(runA, runB);
    }

    public static void findCorrelationsBetweenMetrics() {
        try {
            System.out.println(
                String.format("Kendall's between P@10 (w/o tie breaks) and AP/nDCG (tau): %.4f",
                    findCorrelation(new NoTieBreaker(), Metric.P_10,
                            Metric.AP, QPPCorrelationMode.TAU))
            );

            System.out.println(
                String.format("Kendall's between P@10 (w/ tie breaks) and AP/nDCG (tau): %.4f",
                    findCorrelation(new NoisePerturbationTieBreaker(NUM_RANKINGS, DELTA),
                        Metric.P_10, Metric.AP, QPPCorrelationMode.TAU))
            );

            System.out.println(
                String.format("Kendall's between P@10 (w/o tie breaks) and AP/nDCG (ndcg): %.4f",
                    findCorrelation(new NoTieBreaker(),
                            Metric.P_10, Metric.nDCG, QPPCorrelationMode.NDCG))
            );

//            System.out.println(
//                    String.format("Kendall's between P@10 (w/ tie breaks) and nDCG (ndcg): %.4f",
//                            findCorrelation(new NoisePerturbationTieBreaker(NUM_RANKINGS, DELTA),
//                                    Metric.P_10, Metric.nDCG, QPPCorrelationMode.NDCG))
//            );
        }
        catch (Exception e) { e.printStackTrace();}
    }

    public static void findCorrelationBetweenTieResolvers() {
        try {
            double[] sortedQPPMeasures_notiebreaks =
                runExperiment(new NoTieBreaker(), Metric.P_10, QPPCorrelationMode.TAU);
            double[] sortedQPPMeasures_withtiebreaks =
                runExperiment(new NoisePerturbationTieBreaker(NUM_RANKINGS, DELTA), Metric.P_10, QPPCorrelationMode.TAU);

            double[] sortedQPPMeasures_ndcg =
                runExperiment(new NoTieBreaker(), Metric.P_10, QPPCorrelationMode.NDCG);

            System.out.println(
                String.format("tau (orig, tie-break): %.4f",
                (new KendallsCorrelation())
                .correlation(sortedQPPMeasures_notiebreaks, sortedQPPMeasures_withtiebreaks)
                )
            );

            System.out.println(
                String.format("tau (tie-break, ndcg): %.4f",
                    (new KendallsCorrelation())
                    .correlation(sortedQPPMeasures_withtiebreaks, sortedQPPMeasures_ndcg)
                )
            );

            System.out.println(
                String.format("tau (orig, ndcg): %.4f",
                (new KendallsCorrelation())
                .correlation(sortedQPPMeasures_notiebreaks, sortedQPPMeasures_ndcg)
                )
            );
        }
        catch (Exception e) { e.printStackTrace();}
    }

    public static void main(String[] args) {
        findCorrelationsBetweenMetrics();

        //findCorrelationBetweenTieResolvers();
    }
}
