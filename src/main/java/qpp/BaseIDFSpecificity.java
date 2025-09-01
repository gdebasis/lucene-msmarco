package qpp;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

import java.io.IOException;
import java.util.*;

public abstract class BaseIDFSpecificity extends BaseQPPMethod {
    IndexReader reader;
    IndexSearcher searcher;
    int topK; //top-k cutoff

    public BaseIDFSpecificity() { }

    public BaseIDFSpecificity(IndexSearcher searcher, int k) {
        this.searcher = searcher;
        this.reader = searcher.getIndexReader();
        this.topK = k;
    }

    public void writePermutationMap(List<MsMarcoQuery> queries, Map<String, TopDocs> topDocsMap, int sampleNumber) throws IOException {}
    public void setDataSource(String dataFile) throws IOException { }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        double specificity = 0;
        try {
            specificity = maxIDF(q.getQuery());
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return specificity;
    }

    protected double[] getRSVs(TopDocs topDocs) {
        return Arrays.stream(topDocs.scoreDocs)
                .limit(topK) // only on top-k
                .map(scoreDoc -> scoreDoc.score)
                .mapToDouble(d -> d)
                .toArray();
    }

    protected double[] getRSVs(TopDocs topDocs, int k) {
        return Arrays.stream(topDocs.scoreDocs)
                .limit(k)
                .map(scoreDoc -> scoreDoc.score)
                .mapToDouble(d -> d)
                .toArray();
    }


    protected double maxIDF(Query q) throws IOException {
        long N = reader.numDocs();
        Set<Term> qterms = new HashSet<>();

        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        q.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(qterms);
        // 5.x CODE
        //q.createWeight(searcher, false).extractTerms(qterms);
        //---LUCENE_COMPATIBILITY

        double aggregated_idf = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            if(n != 0){
                double idf = Math.log(N/(double)n);
                if (idf > aggregated_idf)
                    aggregated_idf = idf;
            }
        }
        return aggregated_idf;
    }

    double[] idfs(Query q)  throws IOException {
        long N = reader.numDocs();
        Set<Term> qterms = new HashSet<>();

        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        q.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(qterms);
        // 5.x CODE
        //q.createWeight(searcher, false).extractTerms(qterms);
        //---LUCENE_COMPATIBILITY
        double[] idfs = new double[qterms.size()];

        double aggregated_idf = 0;
        int i = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            if (n==0) n = 1; // avoid 0 error!
            idfs[i++] = Math.log(N/(double)n);;
        }
        return idfs;
    }

    @Override
    public String name() {
        return "MaxIDF";
    }
}
