package retrieval;

import fdbk.RelevanceModelConditional;
import fdbk.RelevanceModelIId;
import fdbk.RetrievedDocTermInfo;
import indexing.MsMarcoIndexer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import qrels.AllRetrievedResults;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FdbkTermStats extends KNNRelModel {
    String resFile;

    FdbkTermStats(String qrelFile, String queryFile, String resFile) throws Exception {
        super(qrelFile, queryFile);
        this.resFile = resFile;
    }

    public void computeFdbkTermWeights(int numTopDocs) throws Exception {
        AllRetrievedResults allRetrievedResults = new AllRetrievedResults(resFile);
        Map<String, TopDocs> topDocsMap = allRetrievedResults.castToTopDocs();

        Map<String, String> testQueries = loadQueries(Constants.QUERY_FILE_TEST);
        testQueries
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                e -> e.getKey(),
                                e -> MsMarcoIndexer.normalizeNumbers(e.getValue())
                        )
                )
        ;

        for (Map.Entry<String, String> e : testQueries.entrySet()) {
            MsMarcoQuery query = new MsMarcoQuery(e.getKey(), e.getValue());
            TopDocs topDocs = topDocsMap.get(query.qid);
            int k = Math.min(numTopDocs, topDocs.scoreDocs.length);
            ScoreDoc[] sd = new ScoreDoc[k];
            System.arraycopy(topDocs.scoreDocs, 0, sd, 0, k);
            TopDocs subset = new TopDocs(topDocs.totalHits, sd);

            printFdbkTerms(searcher, query, subset, numTopDocs);
        }
    }

    void printFdbkTerms(IndexSearcher searcher, MsMarcoQuery query, TopDocs topDocs, int numTopDocs)
            throws Exception {
        RelevanceModelIId fdbkModel = new RelevanceModelConditional(
                searcher, query, topDocs, numTopDocs);
        fdbkModel.computeFdbkWeights();
        System.out.println(query.qid + ": " + query.qText);
        List<RetrievedDocTermInfo> fdbkWeights =
                fdbkModel.getRetrievedDocsTermStats().getTermStats().values()
                        .stream().sorted().collect(Collectors.toList());

        for (RetrievedDocTermInfo x: fdbkWeights) {
            System.out.println(x.getTerm() + ": " + x.getWeight());
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            /*
            System.err.println("usage: java FdbkTermStats <res file> <num top docs>");
            return;
            */
            args = new String[2];
            args[0] = "colbert-on-bm25-trec-dl-queries";
            args[1] = "20";
        }

        try {
            FdbkTermStats fdbkTermStats = new FdbkTermStats(Constants.QRELS_TRAIN, Constants.QUERY_FILE_TRAIN, args[0]);
            fdbkTermStats.computeFdbkTermWeights(Integer.parseInt(args[1]));
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
