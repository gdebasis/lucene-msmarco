package qpp;

import correlation.OverlapStats;
import utils.IndexUtils;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import retrieval.Constants;
import retrieval.MsMarcoQuery;

import java.util.*;

public class RSDSpecificity extends BaseQPPMethod {
    BaseIDFSpecificity qppMethod;
    static Random rnd = new Random(IndexUtils.SEED);
    static final int NUM_SAMPLES = 10;

    public RSDSpecificity(BaseIDFSpecificity qppMethod) {
        this.qppMethod = qppMethod;
    }

    TopDocs sampleTopDocs(TopDocs topDocs, int k) {
//        ScoreDoc[] sampledScoreDocs = new ScoreDoc[k];
        ScoreDoc[] sampledScoreDocs = new ScoreDoc[Math.min(topDocs.scoreDocs.length, k)];
        List<ScoreDoc> sdList = new ArrayList(Arrays.asList(topDocs.scoreDocs));
        Collections.shuffle(sdList, rnd);
        sampledScoreDocs = sdList.subList(0, Math.min(topDocs.scoreDocs.length, k)).toArray(sampledScoreDocs);

        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        return new TopDocs(new TotalHits(k, TotalHits.Relation.EQUAL_TO), sampledScoreDocs);
        // 5.x code
        //return new TopDocs(Math.min(topDocs.scoreDocs.length, k), sampledScoreDocs, SEED);
        //---LUCENE_COMPATIBILITY
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        double avgRankSim = 0;
        //System.out.println("Estimating QPP for query " + q.getId());
        for (int i=0; i < NUM_SAMPLES; i++) {
            //System.out.println("Estimating over sample " + i);
            TopDocs sampledTopDocs = sampleTopDocs(topDocs, qppMethod.topK);
//            TopDocs sampledTopDocs = sampleTopDocs(topDocs, Math.min(Constants.RLM_NUM_TOP_DOCS, topDocs.scoreDocs.length));
            double qppEstimate = qppMethod.computeSpecificity(q, sampledTopDocs);

            double rankSim = OverlapStats.computeRBO(topDocs, sampledTopDocs);
            double w = rankSim * qppEstimate;
            avgRankSim += w;
        }
        return avgRankSim/(double)NUM_SAMPLES;
    }

    @Override
    public String name() {
        return "RSD";
    }
}
