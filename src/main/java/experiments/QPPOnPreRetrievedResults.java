package experiments;
import correlation.KendalCorrelation;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import qpp.*;
import qrels.AllRetrievedResults;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.QueryLoader;
import stochastic_qpp.*;
import utils.IndexUtils;

import java.io.*;
import java.util.*;

public class QPPOnPreRetrievedResults {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
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
            "data/runs/1920/qppres/splade.100.res";
            args[2] = Constants.DL1920_CONTRIEVER_VECS;
        }

        String queryFile = args[0];
        String resFile = args[1];
        String queryVecsFile = args[2];

        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_INDEX).toPath()));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        IndexUtils.init(searcher);

        Map<String, MsMarcoQuery> queryMap = QueryLoader.constructQueryMap(queryFile);
        AllRetrievedResults allRetrievedResults = new AllRetrievedResults(new File(resFile).getPath(), true);

        BufferedWriter bw = new BufferedWriter(new FileWriter(resFile + ".qpp"));

        DocVectorReader denseVecReader =
                new DocVectorReader(Constants.COLL_DENSEVEC_FILE_CONTRIEVER);
        Map<Integer, float[]> queryVecs = QueryVecLoader.load(queryVecsFile);

        final QPPMethod[] qppMethods = {
                new NQCSpecificity(searcher, 50),
                new UEFSpecificity(new NQCSpecificity(searcher, 50)),
                new RSDSpecificity(new NQCSpecificity(searcher, 50)),
                new OddsRatioSpecificity(searcher, 0.2f, 50),  // QPP-PRP
                new WIGSpecificity(searcher, 5),
                new NQCCalibratedSpecificity(searcher, 0.33f, 0.33f, 0.33f, 50),
                new VariantSpecificity(
                        new NQCSpecificity(searcher, 50),
                        searcher,
                        new KNNRelModel(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TEST, false),
                        5, 0.2f,false, 50
                ),
                new DenseVecSpecificity(denseVecReader, queryVecs, Constants.DENSEQPP_NUM_TOP_DOCS),
                //new DenseVecMatryoskaSpecificity(denseVecReader, queryVecs, Constants.DENSEQPP_NUM_TOP_DOCS),
                new SMVSpecificity(searcher, 50),    // SMV (needs searcher + k)
                new SigmaMaxSpecificity(50),
                new SigmaXSpecificity(0.5, 50),
        };

        int count = 0;
        for (String qid: allRetrievedResults.queries()) {

            if (count++ % 5 == 0)
                System.out.print(String.format("QPP completed for %d queries\r", count));

            MsMarcoQuery query = queryMap.get(qid);
            if (query==null)
                continue;

            TopDocs topDocs = allRetrievedResults.castToTopDocs(qid);
            if (topDocs==null || topDocs.scoreDocs.length==0)
                continue;

            StringBuilder sb = new StringBuilder();
            sb.append(qid).append("\t");

            for (QPPMethod qppMethod : qppMethods) {
                float qppEstimate = (float) qppMethod.computeSpecificity(
                        query,
                        topDocs);
                sb.append(qppEstimate).append("\t");
            }

            sb.deleteCharAt(sb.length()-1);
            bw.write(sb.toString());
            bw.newLine();
        }

        bw.close();
        denseVecReader.close();
    }
}
