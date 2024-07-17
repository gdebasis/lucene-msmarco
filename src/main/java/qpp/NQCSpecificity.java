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

    public NQCSpecificity(IndexSearcher searcher) {
        super(searcher);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int k) {
        return computeNQC(q, topDocs, k);
    }

    private double computeNQC(Query q, double[] rsvs, int k) {
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

    public double computeNQC(Query q, RetrievedResults topDocs, int k) {
        return computeNQC(q, topDocs.getRSVs(k), k);
    }

    double[] getRSVs(TopDocs topDocs, int k) {
        return Arrays.stream(topDocs.scoreDocs)
                .limit(k) // only on top-k
                .map(scoreDoc -> scoreDoc.score)
                .mapToDouble(d -> d)
                .toArray();
    }

    public double computeNQC(MsMarcoQuery q, TopDocs topDocs, int k) {
        return computeNQC(q.getQuery(), getRSVs(topDocs, k), k);
    }

    @Override
    public String name() {
        return "nqc";
    }
}
