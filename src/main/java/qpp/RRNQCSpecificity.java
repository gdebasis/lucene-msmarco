package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.Arrays;

public class RRNQCSpecificity extends NQCSpecificity {

    public RRNQCSpecificity(IndexSearcher searcher, int k) {
        super(searcher, k);
    }

    public double computeNQC(Query q, double[] rsvs) {
        rsvs = Arrays.stream(rsvs).limit(topK).toArray();
        double ref = Arrays.stream(rsvs).average().getAsDouble();
        double nqc = 0;
        double del;
        try {
            int rank = 1;
            for (double rsv: rsvs) {
                del = rsv - ref;
                del *= Math.log(1+1.0/(double)(rank + 1000.0));
                nqc += del*del;
                rank++;
            }
            nqc /= (double)rsvs.length;
            return nqc * avgIDF(q); // high variance, high avgIDF -- more specificity
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return 1.0;
    }
}
