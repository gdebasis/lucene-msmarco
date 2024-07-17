package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import qrels.ResultTuple;
import qrels.RetrievedResults;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.TermDistribution;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class VariantSpecificity extends NQCSpecificity {
    QPPMethod baseModel;
    KNNRelModel knnRelModel;
    int numVariants;
    float lambda;
    boolean norlamiseScores;

    public VariantSpecificity(QPPMethod baseModel,
                              IndexSearcher searcher, KNNRelModel knnRelModel,
                              int numVariants,
                              float lambda) {
        this(baseModel, searcher, knnRelModel, numVariants, lambda, false);
    }

    public VariantSpecificity(QPPMethod baseModel,
                              IndexSearcher searcher, KNNRelModel knnRelModel,
                              int numVariants,
                              float lambda, boolean normaliseScores) {
        super(searcher);

        this.baseModel = baseModel;
        this.knnRelModel = knnRelModel;
        this.numVariants = numVariants;
        this.lambda = lambda;
        this.norlamiseScores = normaliseScores;
    }

    TopDocs normaliseScores(TopDocs topDocs) {
        if (!norlamiseScores)
            return topDocs;

        float minScore = Arrays.stream(topDocs.scoreDocs).map(x->x.score).reduce(Float::min).get();
        float maxScore = Arrays.stream(topDocs.scoreDocs).map(x->x.score).reduce(Float::max).get();
        float diff = maxScore - minScore;

        ScoreDoc[] normalisedSDs = new ScoreDoc[topDocs.scoreDocs.length];
        System.arraycopy(topDocs.scoreDocs, 0, normalisedSDs, 0, topDocs.scoreDocs.length);

        for (ScoreDoc sd: normalisedSDs)
            sd.score = (sd.score - minScore)/diff;

        return new TopDocs(topDocs.totalHits, normalisedSDs);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int k) {
        List<MsMarcoQuery> knnQueries = null;
        double variantSpec = 0;

        if (norlamiseScores)
            topDocs = normaliseScores(topDocs);

        try {
            if (numVariants > 0)
                knnQueries = knnRelModel.getKNNs(q, numVariants);

            if (knnQueries!=null && !knnQueries.isEmpty()) {
                variantSpec = variantSpecificity(q, knnQueries, topDocs, k);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }

        return knnQueries!=null?
                lambda * variantSpec + (1-lambda) * baseModel.computeSpecificity(q, topDocs, k):
                baseModel.computeSpecificity(q, topDocs, k);
    }

    double variantSpecificity(MsMarcoQuery q, List<MsMarcoQuery> knnQueries, TopDocs topDocs, int k) throws Exception {
        double specScore = 0;
        double z = 0;
        double variantSpecScore;
        double refSim;

        // apply QPP base model on these estimated relevance scores
        for (MsMarcoQuery rq: knnQueries) {

            TopDocs topDocsRQ = searcher.search(rq.getQuery(), k);

            if (norlamiseScores)
                topDocsRQ = normaliseScores(topDocsRQ);

            //System.out.println("var scores after norm:");
            //varInfo.getTuples().stream().limit(5).forEach(System.out::println);

            variantSpecScore = baseModel.computeSpecificity(rq, topDocsRQ, k);
            refSim = rq.getRefSim();

            //System.out.println(String.format("%s %.4f", rq.getId(), variantSpecScore));
            specScore +=  refSim * variantSpecScore ;
            z += refSim;
        }

        return z==0? baseModel.computeSpecificity(q, topDocs, k): specScore/z;
    }

}
