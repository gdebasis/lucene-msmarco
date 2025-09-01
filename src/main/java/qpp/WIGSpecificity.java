package qpp;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import qrels.RetrievedResults;
import retrieval.MsMarcoQuery;

import java.util.Set;

public class WIGSpecificity extends BaseIDFSpecificity {

    public WIGSpecificity(IndexSearcher searcher, int k) {
        super(searcher,k);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        // delegate to the k-version using the default `this.k`
        return computeSpecificity(q, topDocs, this.k);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int k) {
        double avgIDF = 0;
        int numQueryTerms = 1;
        try {
            Set<Term> qterms = q.getQueryTerms();
            numQueryTerms = qterms.size();
            avgIDF = 1/maxIDF(q.getQuery());
        }
        catch (Exception ex) { ex.printStackTrace(); }
        /*
        double[] rsvs = getRSVs(topDocs);
        double wig = 0;

        for (double rsv: rsvs) {
            wig += (rsv - avgIDF);
        }
//        return wig/(double)(Math.sqrt(numQueryTerms) * rsvs.length);
        return wig/(wigdouble)(numQueryTerms * rsvs.length);
         */

        double[] rsvs = getRSVs(topDocs);
        int cutoff = Math.min(k, rsvs.length);

        double wig = 0;
        for (int i = 0; i < cutoff; i++) {
            wig += (rsvs[i] - avgIDF);
        }

        return wig / (double)(numQueryTerms * cutoff);
    }

    @Override
    public String name() {
        return "wig";
    }
}
