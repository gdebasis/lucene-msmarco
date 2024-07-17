package stochastic_qpp;

import correlation.KendalCorrelation;
import correlation.QPPCorrelationMetric;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import qpp.*;
import qrels.Metric;
import qrels.PreEvaluatedResults;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;
import utils.IndexUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class QPPOnPreRetrievedResults {
    Metric targetMetric;
    PreRetrievedResults preRetrievedResults;
    PreEvaluatedResults preEvaluatedResults;
    QPPCorrelationMetric qppCorrelationMetric;

    public QPPOnPreRetrievedResults(IndexReader reader, String resFile, String evalResFile) throws Exception {
        System.out.println("Loading results from " + resFile);
        preRetrievedResults = new PreRetrievedResults(reader, new File(resFile));

        System.out.println("Loading per-query evaluation from " + evalResFile);
        preEvaluatedResults = new PreEvaluatedResults(evalResFile);
    }

    public void configure(Metric targetMetric, QPPCorrelationMetric qppCorrelationMetric) {
        this.targetMetric = targetMetric;
        this.qppCorrelationMetric = qppCorrelationMetric;
    }

    double evaluate(QPPMethod qppMethod) {
        Set<String> qids = preEvaluatedResults.getQueryIds();
        List<Double> gt_vals = new ArrayList<>();
        List<Double> pred_vals = new ArrayList<>();

        for (String qid : qids) {
            double eval = preEvaluatedResults.compute(qid, targetMetric);
            if (eval >= 0) {
                gt_vals.add(eval);
                pred_vals.add(preRetrievedResults.runQPP(qid, qppMethod));
            }
        }

        double[] gt_vals_array = gt_vals.stream().mapToDouble(x -> x).toArray();
        double[] pred_vals_array = pred_vals.stream().mapToDouble(x -> x).toArray();

        return qppCorrelationMetric.correlation(gt_vals_array, pred_vals_array);
    }

    public static void main(String[] args) {
        final Metric[] targetMetricNames = {Metric.AWRF, Metric.nDCG, Metric.AWRF_NDCG};
        QPPMethod[] qppMethods = {
                new NQCSpecificity(),
                new CumulativeNQC(),
                new RSDSpecificity(new NQCSpecificity())
        };

        if (args.length < 2) {
            args = new String[2];
            args[0] = "/Users/debasis/research/fair_ir/runs/coordinators_runs/input.0mt5";
            args[1] = "/Users/debasis/research/fair_ir/runs/coordinators_summary/summary.0mt5.coord.tsv";
        }

        String resFile = args[0];
        String evalResFile = args[1];
        String indexDir = Constants.TREC_FAIR_IR_INDEX;

        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexDir).toPath()));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            IndexUtils.init(searcher);

            QPPOnPreRetrievedResults qppOnPreRetrievedResults = new QPPOnPreRetrievedResults(reader, resFile, evalResFile);

            for (Metric targetMetric : targetMetricNames) {
                qppOnPreRetrievedResults.configure(targetMetric, new KendalCorrelation());

                for (QPPMethod qppMethod : qppMethods) {
                    double corr = qppOnPreRetrievedResults.evaluate(qppMethod);
                    System.out.println(String.format("%s %s: %s = %.4f",
                            qppMethod.name(), targetMetric.toString(),
                            qppOnPreRetrievedResults.qppCorrelationMetric.name(),
                            corr));
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
