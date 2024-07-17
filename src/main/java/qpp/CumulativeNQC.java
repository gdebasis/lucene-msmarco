package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

public class CumulativeNQC extends NQCSpecificity {
    public CumulativeNQC() {}

    public CumulativeNQC(IndexSearcher searcher) {
        super(searcher);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int k) {
        double s = 0;
        for (int i=1; i < k; i++) {
            s += computeNQC(q, topDocs, i);
        }
        return s/k;
    }

    @Override
    public String name() {
        return "cumnqc";
    }
}
