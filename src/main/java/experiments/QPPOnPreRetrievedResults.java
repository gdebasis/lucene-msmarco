package experiments;
import correlation.KendalCorrelation;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import qpp.*;
import qrels.AllRetrievedResults;
import qrels.Evaluator;
import qrels.Metric;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.QueryLoader;
import stochastic_qpp.*;
import utils.IndexUtils;

import java.io.*;
import java.util.*;

public class QPPOnPreRetrievedResults {
    // static final String BM25_MSMARCO_DEV_TOP100 = "runs/bm25_100_msmarcodev.res";
    // static boolean EVALUATE = true;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Arguments expected: <query file> <TREC formatted res file>");
            args = new String[3];
            args[0] = Constants.QUERIES_DL1920;
            args[1] =
//                    "data/runs/1920/dense_qpp_another_calculation/BM25.1920.100.res";
//            "data/runs/1920/dense_qpp_another_calculation/colbert.e2e.100.res";
//            "data/runs/1920/dense_qpp_another_calculation/e5_dl_1920.100.res";
//            "data/runs/1920/dense_qpp_another_calculation/monot5.100.res";
//            "data/runs/1920/dense_qpp_another_calculation/prf_rank_beta05.1920.100.res";
//            "data/runs/1920/dense_qpp_another_calculation/prf_rerank_beta05.1920.100.res";
//            "data/runs/1920/dense_qpp_another_calculation/rm3.100.res";
            "data/runs/1920/dense_qpp_another_calculation/splade.100.res";
            //args[2] = Constants.QRELS_DL20;
        }

        String queryFile = args[0];
        String resFile = args[1];
        //String qrelsFile = args[2];

        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_INDEX).toPath()));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        IndexUtils.init(searcher);

        Map<String, MsMarcoQuery> queryMap = QueryLoader.constructQueryMap(queryFile);
        AllRetrievedResults allRetrievedResults = new AllRetrievedResults(new File(resFile).getPath(), true);

        //Evaluator evaluator = new Evaluator(qrelsFile, allRetrievedResults); // Metrics for top-100 (P@10 is still at 10)
        //double[] evaluatedMetricValues = new double[queryMap.values().size()];
//        int i=0;
//        for (MsMarcoQuery query: queryMap.values()) {
//            evaluatedMetricValues[i++] = evaluator.compute(query.getId(), Metric.AP);
//        }

        BufferedWriter bw = new BufferedWriter(new FileWriter(resFile + ".qpp"));

        DocVectorReader denseVecReader =
                new DocVectorReader(Constants.COLL_DENSEVEC_FILE_CONTRIEVER);
        Map<Integer, float[]> queryVecs = QueryVecLoader.load(Constants.DL1920_CONTRIEVER_VECS);

        final QPPMethod[] qppMethods = {
                new NQCSpecificity(searcher, 100),
                new UEFSpecificity(new NQCSpecificity(searcher, 100)),
                new RSDSpecificity(new NQCSpecificity(searcher, 100)),
                new OddsRatioSpecificity(searcher, 0.2f, 50),  // QPP-PRP
                new WIGSpecificity(searcher, 5),
                new NQCCalibratedSpecificity(searcher, 0.33f, 0.33f, 0.33f, 100),
//                new VariantSpecificity(
//                        new NQCSpecificity(searcher, 50),
//                        searcher,
//                        new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST, false),
//                        5, 0.2f,false, 50
//                ),
//                new DenseVecSpecificity(denseVecReader, queryVecs, Constants.DENSEQPP_NUM_TOP_DOCS),
                new DenseVecMatryoskaSpecificity(denseVecReader, queryVecs, Constants.DENSEQPP_NUM_TOP_DOCS),
                new SMVSpecificity(searcher, 50),    // SMV (needs searcher + k)
                new SigmaMaxSpecificity(50),
                new SigmaXSpecificity(0.5, 50),
        };

        double[][] qppScores = new double[qppMethods.length][queryMap.values().size()];

        int count = 0;
        // int queryIndex = 0;
        for (String qid: allRetrievedResults.queries()) {
            if (count++ % 5 == 0)
                System.out.print(String.format("QPP completed for %d queries\r", count));

            StringBuilder sb = new StringBuilder();
            sb.append(qid).append("\t");

            // int qppMethodIndex = 0;
            for (QPPMethod qppMethod : qppMethods) {
//                float qppEstimate = (float) qppMethod.computeSpecificity(
//                        queryMap.get(qid),
//                        allRetrievedResults.castToTopDocs(qid),
//                        Constants.QPP_NUM_TOPK);
                float qppEstimate = (float) qppMethod.computeSpecificity(
                        queryMap.get(qid),
                        allRetrievedResults.castToTopDocs(qid));
                //qppScores[qppMethodIndex++][queryIndex] = qppEstimate;
                sb.append(qppEstimate).append("\t");
            }

            sb.deleteCharAt(sb.length()-1);
            bw.write(sb.toString());
            bw.newLine();
            //queryIndex++;
        }

        bw.close();
        denseVecReader.close();

        // Optional evaluation flow
//        if (EVALUATE) {
//            for (int qppMethodIndex = 0; qppMethodIndex < qppMethods.length; qppMethodIndex++) {
//                double[] qppScoresForASingleQuery = qppScores[qppMethodIndex];
//                double kendalls = new KendalCorrelation().correlation(evaluatedMetricValues, qppScoresForASingleQuery);
//                System.out.println(String.format("%s: %.4f", qppMethods[qppMethodIndex].name(), kendalls));
//            }
//        }
    }
}
