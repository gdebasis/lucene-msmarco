package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

import java.io.IOException;
import java.util.Arrays;

public class SMVSpecificity extends BaseIDFSpecificity {

    public SMVSpecificity(IndexSearcher searcher, int k) {
        super(searcher, k);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        return computeSMV(q.getQuery(), getRSVs(topDocs, this.topK));
    }

    public double computeSMV(Query q, double[] rsvs) {
        if (rsvs.length == 0) return 0.0;

        // limit to cutoff k
        rsvs = Arrays.stream(rsvs).limit(topK).toArray();

        double muHat = Arrays.stream(rsvs).average().orElse(1.0);

        double scoreD = 0;
        double smv = 0.0;
        for (double score : rsvs) {
            if (score > 0 && muHat > 0) {
                smv += score * Math.abs(Math.log(score/muHat));
            }
        }
        smv /= rsvs.length;
        try {
            scoreD = reader!=null? Arrays.stream(idfs(q)).average().getAsDouble() : 1.0;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return smv *scoreD; // high variance, high avgIDF -- more specificity
    }

    @Override
    public String name() {
        return "smv";
    }
}

//def SMV(score_list, k):
//corpus_score = np.mean(score_list)                  # mean over whole list
//mu = np.mean(score_list[:k])                        # mean of top-k
//        smv_norm = np.mean(np.array(score_list[:k]) * abs(np.log(score_list[:k]/mu))) / corpus_score
//        smv_no_norm = np.mean(np.array(score_list[:k]) * abs(np.log(score_list[:k]/mu)))
//        return smv_norm, smv_no_norm
