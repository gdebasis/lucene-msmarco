package qpp;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;
import qrels.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AvgIDFSpecificity extends BaseQPPMethod {
    IndexReader reader;
    IndexSearcher searcher;
    int k;

    public AvgIDFSpecificity(IndexSearcher searcher, int k) {
        this.searcher = searcher;
        this.reader = searcher.getIndexReader();
        this.k=k;
    }

    public void writePermutationMap(List<MsMarcoQuery> queries, Map<String, TopDocs> topDocsMap, int sampleNumber) throws IOException {}
    public void setDataSource(String dataFile) throws IOException { }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        double specificity = 0;
        try {
            specificity = averageIDF(q.getQuery());
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return specificity;
    }

    double averageIDF(Query q) throws IOException {
        long N = reader.numDocs();
        Set<Term> qterms = new HashSet<>();
        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        q.createWeight(searcher, ScoreMode.COMPLETE, 1).extractTerms(qterms);
        // 5.x code
        //q.createWeight(searcher, false).extractTerms(qterms);
        //---LUCENE_COMPATIBILITY

        float aggregated_idf = 0;
        for (Term t: qterms) {
            int n = reader.docFreq(t);
            if(n != 0){
                double idf = Math.log(N/(double)n);
                aggregated_idf += idf;
            }
        }
        return aggregated_idf/(double)qterms.size();
    }

    @Override
    public String name() {
        return "avgidf";
    }
}
