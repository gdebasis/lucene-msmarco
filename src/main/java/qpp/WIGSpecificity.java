package qpp;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

import java.util.Arrays;
import java.util.Set;

public class WIGSpecificity extends BaseIDFSpecificity {

    public WIGSpecificity(IndexSearcher searcher, int k) {
        super(searcher,k);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
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
        double avgScore = Arrays.stream(rsvs).average().getAsDouble();

        int cutoff = Math.min(topK, rsvs.length);

        double wig = 0;
        for (int i = 0; i < cutoff; i++) {
            wig += (rsvs[i] - avgIDF);
            //wig += Math.log(1 + rsvs[i]/avgScore);
        }

        return wig / (double)(numQueryTerms * cutoff);
        //return wig/(double)numQueryTerms * avgIDF;
    }

    @Override
    public String name() {
        return "wig";
    }
}
