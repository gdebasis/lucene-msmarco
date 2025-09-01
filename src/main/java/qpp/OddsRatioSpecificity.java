package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

import java.util.Arrays;

public class OddsRatioSpecificity extends BaseIDFSpecificity {
    float p;

    public OddsRatioSpecificity(IndexSearcher searcher, float p, int k) {
        super(searcher, k);
        this.p = p;
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        int topK = (int)(p * Math.min(this.topK,topDocs.scoreDocs.length));
        int bottomK = topK;

        double[] rsvs = getRSVs(topDocs);
        double avgIDF = 0;
        try {
            avgIDF = Arrays.stream(idfs(q.getQuery())).max().getAsDouble();
        }
        catch (Exception ex) { ex.printStackTrace(); }

        double topAvg = Arrays.stream(rsvs).limit(topK).average().getAsDouble();
        double bottomAvg = Arrays.stream(rsvs).skip(topK-bottomK).average().getAsDouble();
        return topAvg/bottomAvg * avgIDF;
    }

    @Override
    public String name() {
        return "odds-ratio";
    }
}

