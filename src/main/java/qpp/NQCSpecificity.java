package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;
import qrels.RetrievedResults;

import java.io.IOException;
import java.util.Arrays;

public class NQCSpecificity extends BaseIDFSpecificity {

    public NQCSpecificity() { }

    public NQCSpecificity(IndexSearcher searcher, int k) {
        super(searcher,k);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        return computeNQC(q, topDocs);
    }

    /**
     * Version 1: uses the class field k (default)
     */
    public double computeNQC(MsMarcoQuery q, TopDocs topDocs) {
        return computeNQC(q.getQuery(), getRSVs(topDocs, this.k));
    }

    /**
     * Version 2: explicit cutoff k (for special cases like CumulativeNQC)
     */
    public double computeNQC(MsMarcoQuery q, TopDocs topDocs, int k) {
        return computeNQC(q.getQuery(), getRSVs(topDocs, k), k);
    }

    /** NEW: Base version to allow subclasses to override */
    public double computeNQC(Query q, double[] rsvs, int k) {
        // default behavior: just ignore k and use the simpler version
        rsvs = Arrays.stream(rsvs).limit(k).toArray();
        return computeNQC(q, rsvs);
    }

    public double computeNQC(Query q, double[] rsvs) {
        rsvs = Arrays.stream(rsvs).limit(k).toArray();

        //double ref = new StandardDeviation().evaluate(rsvs);
        double ref = Arrays.stream(rsvs).average().getAsDouble();
        double avgIDF = 0;
        double nqc = 0;
        double del;
        for (double rsv: rsvs) {
            del = rsv - ref;
            nqc += del*del;
        }
        nqc /= (double)rsvs.length;

        try {
            avgIDF = reader!=null? Arrays.stream(idfs(q)).average().getAsDouble() : 1.0;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return nqc * avgIDF; // high variance, high avgIDF -- more specificity
    }

    public double computeNQC(Query q, RetrievedResults topDocs) {
        return computeNQC(q, topDocs.getRSVs(k), k);
    }

/*
    public double computeNQC(MsMarcoQuery q, TopDocs topDocs) {
        return computeNQC(q.getQuery(), getRSVs(topDocs));
    }
*/

    @Override
    public String name() {
        return "nqc";
    }
}
