package retrieval;

import indexing.MsMarcoIndexer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.json.simple.JSONObject;
import qrels.AllRelRcds;
import qrels.PerQueryRelDocs;

import java.util.*;
import java.util.stream.Collectors;

public class MsMarcoQuery implements Comparable<MsMarcoQuery> {
    String qid;
    String qText;
    Query query;
    JSONObject fewshotInfo;
    float simWithOrig;
    PerQueryRelDocs relDocs;

    public MsMarcoQuery(String qid, String qText) {
        this(qid, qText, 1);
    }

    public MsMarcoQuery(String qid, String qText, float simWithOrig) {
        this.qid = qid;
        this.qText = qText;
        this.simWithOrig = simWithOrig;
        makeQuery();
    }

    public MsMarcoQuery(MsMarcoQuery that, Query query) {
        this.qid = that.qid;
        this.qText = that.qText;
        this.simWithOrig = that.simWithOrig;
        this.query = query;
    }

    public MsMarcoQuery(IndexSearcher searcher, String qid, Query query) {
        this.qid = qid;
        this.query = query;
        try {
            Set<Term> origTerms = new HashSet<>();
            query.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(origTerms);
            qText = origTerms.stream().map(x->x.text()).collect(Collectors.joining(" "));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public MsMarcoQuery(String qid, String qText, Query query) {
        this(qid, qText, 0.0f);
        this.query = query;
    }

    public Set<String> getQueryTermsAsString() {
        return Arrays.stream(MsMarcoIndexer
            .analyze(MsMarcoIndexer.constructAnalyzer(), qText)
            .split("\\s+"))
            .collect(Collectors.toSet());
    }

    public Set<Term> getQueryTerms() {
        return Arrays.stream(MsMarcoIndexer
                .analyze(MsMarcoIndexer.constructAnalyzer(), qText)
                .split("\\s+"))
                .map(x-> new Term(Constants.CONTENT_FIELD, x))
                .collect(Collectors.toSet());
    }

    void makeQuery() {
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        String[] tokens = MsMarcoIndexer
                .analyze(MsMarcoIndexer.constructAnalyzer(), this.qText).split("\\s+");
        for (String token: tokens) {
            TermQuery tq = new TermQuery(new Term(Constants.CONTENT_FIELD, token));
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        query = qb.build();
    }

    public List<MsMarcoQuery> retrieveSimilarQueries(
            AllRelRcds rels,
            IndexSearcher qIndexSearcher,
            int k) throws Exception {
        List<MsMarcoQuery> knnQueries = new ArrayList<>();

        TopDocs knnQueriesTopDocs = qIndexSearcher.search(this.query, k);
        double scoreSum = 0;
        for (ScoreDoc sd : knnQueriesTopDocs.scoreDocs) {
            Document q = qIndexSearcher.getIndexReader().document(sd.doc);
            MsMarcoQuery rq = new MsMarcoQuery(
                q.get(Constants.ID_FIELD),
                q.get(Constants.CONTENT_FIELD),
                sd.score);
            rq.makeQuery();

            knnQueries.add(rq);
            // TODO-DV: This has to work with cosine-sim of dense query vectors
            rq.simWithOrig = sd.score;
            rq.relDocs = rels.getRelInfo(rq.qid);
            scoreSum += rq.simWithOrig;
        }

        for (MsMarcoQuery rq: knnQueries) {
            rq.simWithOrig /= scoreSum;
        }

        return knnQueries;
    }

    public List<MsMarcoQuery> retrieveSimilarQueries(
            IndexSearcher qIndexSearcher,
            int k) throws Exception {
        List<MsMarcoQuery> knnQueries = new ArrayList<>();

        TopDocs knnQueriesTopDocs = qIndexSearcher.search(this.query, k);
        double scoreSum = 0;
        for (ScoreDoc sd : knnQueriesTopDocs.scoreDocs) {
            Document q = qIndexSearcher.getIndexReader().document(sd.doc);
            MsMarcoQuery rq = new MsMarcoQuery(
                    q.get(Constants.ID_FIELD),
                    q.get(Constants.CONTENT_FIELD),
                    sd.score);
            rq.makeQuery();

            knnQueries.add(rq);
            // TODO-DV: This has to work with cosine-sim of dense query vectors
            rq.simWithOrig = sd.score;
            scoreSum += rq.simWithOrig;
        }

        for (MsMarcoQuery rq: knnQueries) {
            rq.simWithOrig /= scoreSum;
        }

        return knnQueries;
    }

    public Query getQuery() { return query; }
    public String getId() { return qid; }

    public PerQueryRelDocs getRelDocSet() { return relDocs; }

    public float getRefSim() {
        return simWithOrig;
    }
    public void setRefSim(float refSim) { simWithOrig = refSim; }

    public String toString() {
        return String.format("%s, %s: (%.4f)", qText, query, simWithOrig);
    }

    @Override
    public int compareTo(MsMarcoQuery o) {
        return Float.compare(this.simWithOrig, o.simWithOrig);
    }
}

