package stochastic_qpp;

import correlation.KendalCorrelation;
import correlation.QPPCorrelationMetric;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.EmptyFileFilter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import qpp.*;
import qrels.AllRetrievedResults;
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
    static Map<String, Float> inducedDocScoreCache = new HashMap<>();

    public QPPOnPreRetrievedResults(IndexReader reader, String resFile, String queryFile) throws Exception {
        System.out.println("Loading results from " + resFile);
        preRetrievedResults = new PreRetrievedResults(reader, resFile, queryFile, inducedDocScoreCache);
    }

    public QPPOnPreRetrievedResults(IndexReader reader, String resFile, String evalResFile, String queryFile) throws Exception {
        System.out.println("Loading results from " + resFile);
        preRetrievedResults = new PreRetrievedResults(reader, resFile, queryFile, inducedDocScoreCache);

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

        //System.out.println(Arrays.toString(gt_vals_array));
        //System.out.println(Arrays.toString(pred_vals_array));

        return qppCorrelationMetric.correlation(gt_vals_array, pred_vals_array);
    }

    static double evaluate(QPPOnPreRetrievedResults qppOnPreRetrievedResults,
                           QPPMethod qppMethod, Metric targetMetric,
                           QPPCorrelationMetric correlationMetric) {
        qppOnPreRetrievedResults.configure(targetMetric, correlationMetric);
        return qppOnPreRetrievedResults.evaluate(qppMethod);
    }

    public static void main(String[] args) {

        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Constants.TREC_FAIR_IR_INDEX).toPath()));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            IndexUtils.init(searcher);

            final Metric[] targetMetricNames = {Metric.AWRF, Metric.nDCG, Metric.AWRF_NDCG};
            final QPPMethod[] qppMethods = {
                    new NQCSpecificity(searcher, 50),
                    new CumulativeNQC(searcher, 50),
                    //new RSDSpecificity(new NQCSpecificity(searcher))
            };


            final QPPCorrelationMetric correlationMetric = new KendalCorrelation();

            File[] resFiles = new File(Constants.TREC_FAIR_IR_RESDIR).listFiles();
            File[] evalFiles = new File(Constants.TREC_FAIR_IR_EVALDIR).listFiles();

            Arrays.sort(resFiles);
            Arrays.sort(evalFiles);

            int N = resFiles.length;
            QPPOnPreRetrievedResults qppOnPreRetrievedResults[] = new QPPOnPreRetrievedResults[N];

            for (int i = 0; i < N; i++) {
                qppOnPreRetrievedResults[i] =
                        new QPPOnPreRetrievedResults(
                                reader, resFiles[i].getPath(),
                                evalFiles[i].getPath(), Constants.TREC_FAIR_IR_QUERY_FILE
                        );
            }

            for (Metric targetMetric : targetMetricNames) { // nDCG, AWRF
                for (QPPMethod qppMethod : qppMethods) { // NQC, CNQC
                    double avg_corr = 0;
                    for (int i=0; i < N; i++) { // aggregate over each o/p
                        double corr = evaluate(
                                qppOnPreRetrievedResults[i],
                                qppMethod, targetMetric, correlationMetric
                        );
                        avg_corr += corr;
                    }

                    System.out.println(String.format("%s %s: %s = %.4f",
                            qppMethod.name(), targetMetric.toString(),
                            correlationMetric.name(),
                            avg_corr/N));
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
