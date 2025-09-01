package qpp;

import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.ScoreDoc;
import retrieval.MsMarcoQuery;

import java.util.Arrays;

public class SigmaXSpecificity extends BaseQPPMethod {
    int topK;
    final double x;  // threshold parameter (e.g., 0.5 = 50%)

    public SigmaXSpecificity(double x, int topK) {
        this.x = x;
        this.topK = topK;
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        if (topDocs == null || topDocs.scoreDocs.length == 0) {
            return 0.0;
        }

        int cutoff = Math.min(topK, topDocs.scoreDocs.length);
        float topScore = topDocs.scoreDocs[0].score;

        // collect scores >= x * topScore within cutoff
        double[] scores = Arrays.stream(topDocs.scoreDocs)
                .limit(cutoff)
                .mapToDouble(sd -> sd.score)
                .filter(score -> score >= topScore * x)
                .toArray();

        if (scores.length == 0) {
            return 0.0;
        }

        double mean = Arrays.stream(scores).average().orElse(0.0);

        double variance = Arrays.stream(scores)
                .map(s -> {
                    double d = s - mean;
                    return d * d;
                })
                .average()
                .orElse(0.0);

        double sigma = Math.sqrt(variance);

        int qlen = q.getQueryTerms().size();
        return sigma / Math.sqrt(qlen);
    }

    @Override
    public String name() {
        return String.format("SigmaX-%.2f", x);
    }
}


//def SIGMA_X(qtokens, score_list, x):
//
//top_score = score_list[0]
//scores = []
//
//        for idx, score in enumerate(score_list):
//        if score>=(top_score*x):
//        scores.append(score)
//
//    return np.std(scores)/np.sqrt(len(qtokens)), len(scores)