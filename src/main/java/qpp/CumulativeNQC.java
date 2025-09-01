package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

public class CumulativeNQC extends NQCSpecificity {
    public CumulativeNQC() {}

    public CumulativeNQC(IndexSearcher searcher, int k) {
        super(searcher, k);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        double s = 0;
        for (int i = 1; i < this.topK; i++) {
            s += computeNQC(q, topDocs, i);
        }
        return s/this.topK;
    }

    @Override
    public String name() {
        return "cumnqc";
    }
}
