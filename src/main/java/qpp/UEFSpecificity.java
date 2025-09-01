package qpp;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import correlation.OverlapStats;
import utils.IndexUtils;
import fdbk.RelevanceModelConditional;
import fdbk.RelevanceModelIId;
import retrieval.Constants;
import retrieval.MsMarcoQuery;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class UEFSpecificity extends BaseQPPMethod {
    BaseIDFSpecificity qppMethod;
    static Random rnd = new Random(IndexUtils.SEED);
    static final int NUM_SAMPLES = 10;

    public UEFSpecificity(BaseIDFSpecificity qppMethod) {
        this.qppMethod = qppMethod;
    }

    TopDocs sampleTopDocs(TopDocs topDocs, int k) {
        ScoreDoc[] sampledScoreDocs = new ScoreDoc[k];
        List<ScoreDoc> sdList = new ArrayList<>(Arrays.asList(topDocs.scoreDocs));
        Collections.shuffle(sdList, rnd);
        sampledScoreDocs = sdList.subList(0, Math.min(topDocs.scoreDocs.length, k)).toArray(sampledScoreDocs);

        //+++LUCENE_COMPATIBILITY: Sad there's no #ifdef like C!
        // 8.x CODE
        return new TopDocs(new TotalHits(k, TotalHits.Relation.EQUAL_TO), sampledScoreDocs);
        // 5.x code
        //return new TopDocs(Math.min(topDocs.scoreDocs.length, k), sampledScoreDocs, SEED);
        //---LUCENE_COMPATIBILITY
    }

    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        TopDocs topDocs_rr = null;
        double avgRankDist = 0;
        RelevanceModelIId rlm = null;

        for (int i=0; i < NUM_SAMPLES; i++) {
//            TopDocs sampledTopDocs = sampleTopDocs(topDocs, Math.min(Constants.RLM_NUM_TOP_DOCS, topDocs.scoreDocs.length));
            TopDocs sampledTopDocs = sampleTopDocs(topDocs, qppMethod.topK);
            try {
                rlm = new RelevanceModelConditional(
                        qppMethod.searcher, q, sampledTopDocs, sampledTopDocs.scoreDocs.length);
                rlm.computeFdbkWeights();
            }
            catch (NullPointerException nex) { continue; /* next sample */ }
            catch (IOException ioex) { ioex.printStackTrace(); } catch (Exception ex) {
                Logger.getLogger(UEFSpecificity.class.getName()).log(Level.SEVERE, null, ex);
            }

            topDocs_rr = rlm.rerankDocs();
            double rankDist = OverlapStats.computeRankDist(topDocs, topDocs_rr);
            avgRankDist += rankDist;
        }
        return ((double)NUM_SAMPLES/avgRankDist) * qppMethod.computeSpecificity(q, topDocs);
    }

    @Override
    public String name() {
        return String.format("uef_%s", this.qppMethod.name());
    }
}
