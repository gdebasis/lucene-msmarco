package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import qrels.RetrievedResults;
import retrieval.MsMarcoQuery;

import java.io.IOException;
import java.util.Arrays;

public class NQCCalibratedSpecificity extends NQCSpecificity {
    float alpha, beta, gamma;

    public NQCCalibratedSpecificity(IndexSearcher searcher, int k) {
        super(searcher, k);
    }

    public NQCCalibratedSpecificity(IndexSearcher searcher, float alpha, float beta, float gamma, int k) {
        super(searcher, k);
        setParameters(alpha, beta, gamma);
    }

    public void setParameters(float alpha, float beta, float gamma) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
    }

    @Override
    public double computeNQC(Query q, double[] rsvs, int k) {
        rsvs = Arrays.stream(rsvs).limit(k).toArray();
        double mean = Arrays.stream(rsvs).average().getAsDouble();

        double avgIDF = 0;
        try {
            avgIDF = maxIDF(q);
        } catch (IOException e) {
            e.printStackTrace();
        }


        double nqc = 0;
        for (double rsv: rsvs) {
            double factor_1 = avgIDF;
            // only works for a square function; beta is to be even; we force it to be even
            double factor_2 = (rsv - mean)*(rsv - mean)/rsv;

            double prod = Math.pow(factor_1, alpha) * Math.pow(factor_2, beta); // this is actually 2*beta
            prod = Math.pow(prod, gamma);

            nqc += prod;
        }
        nqc /= (double)rsvs.length;

        return nqc * avgIDF; // high variance, high avgIDF -- more specificity
    }

     public double computeNQC(Query q, RetrievedResults topDocs, int k) {
        return computeNQC(q, topDocs.getRSVs(k), k);
    }

    @Override
    public String name() {
        return String.format("snqc_%.2f-%.2f-%.2f", alpha, beta, gamma);
    }
}
