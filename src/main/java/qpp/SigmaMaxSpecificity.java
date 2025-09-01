package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.ScoreDoc;
import retrieval.MsMarcoQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;

public class SigmaMaxSpecificity extends BaseQPPMethod {
    int topK;

    public SigmaMaxSpecificity(int topK) { this.topK = topK; }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        if (topDocs == null || topDocs.scoreDocs.length == 0) {
            return 0.0;
        }

        // limit to cutoff k
        int cutoff = Math.min(topK, topDocs.scoreDocs.length);
        List<Double> scores = new ArrayList<>();
        double maxStd = 0.0;

        for (int i = 0; i < cutoff; i++) {
            scores.add((double) topDocs.scoreDocs[i].score);

            // mean via streams
            double mean = scores.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            // variance via streams
            double variance = scores.stream()
                    .mapToDouble(s -> Math.pow(s - mean, 2))
                    .average()
                    .orElse(0.0);

            double sigma = Math.sqrt(variance);
            maxStd = Math.max(maxStd, sigma);
        }

        // Normalize by sqrt(|q tokens|)
        int numTerms = q.getQueryTerms().size();
        double norm = Math.sqrt(Math.max(1, numTerms)); // avoid /0
        return maxStd/norm;
    }

    @Override
    public String name() {
        return "SigmaMax";
    }
}



//def SIGMA_MAX(score_list):
//max_std=0
//scores=[]
//
//        for idx, score in enumerate(score_list):
//        scores.append(score)
//        if np.std(scores)>max_std:
//max_std = np.std(scores)
//
//    return max_std, len(scores)