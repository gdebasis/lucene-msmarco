package stochastic_qpp;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.TopDocs;
import qpp.QPPMethod;
import qrels.AllRetrievedResults;
import retrieval.MsMarcoQuery;
import retrieval.QueryLoader;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreRetrievedResults {
    String name;
    Map<String, MsMarcoQuery> queries;
    Map<String, TopDocs> topDocsMap; // a list of topDocs for the query set

    public PreRetrievedResults(PreRetrievedResults that) {
        this.name = that.name;
        this.topDocsMap = new HashMap<>(that.topDocsMap);
    }

    public PreRetrievedResults(String queryFile, String resFile) throws Exception {
        this.name = new File(resFile).getName();
        queries = QueryLoader.constructQueryMap(queryFile);
    }

    public PreRetrievedResults(IndexReader reader, String resFile,
        String queryFile, Map<String, Float> inducedDocScoreCache) throws Exception {

        this(queryFile, resFile);

        AllRetrievedResults allRetrievedResults = new AllRetrievedResults(new File(resFile).getPath(), true);

        // induce the ranks and scores because here it's a minimalist result file with no score.
        //System.out.println("Inducing scores...");
        allRetrievedResults.induceScores(reader, queries, inducedDocScoreCache);
        topDocsMap = allRetrievedResults.castToTopDocs();
    }

    public String toString() {
        return String.format("%s: %.4f", name);
    }

    public TopDocs getTopDocs(String qid) { return topDocsMap.get(qid); }

    public double runQPP(String qid, QPPMethod qppMethod) {
        MsMarcoQuery q = new MsMarcoQuery(qid, "");
        TopDocs topDocs = topDocsMap.get(qid);
        return qppMethod.computeSpecificity(q, topDocs);
    }
}


