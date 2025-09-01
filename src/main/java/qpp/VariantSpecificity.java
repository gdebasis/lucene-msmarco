package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;

import java.util.Arrays;
import java.util.List;

public class VariantSpecificity extends NQCSpecificity {
    QPPMethod baseModel;
    KNNRelModel knnRelModel;
    int numVariants;
    float lambda;
    boolean norlamiseScores;

    public VariantSpecificity(QPPMethod baseModel,
                              IndexSearcher searcher, KNNRelModel knnRelModel,
                              int numVariants,
                              float lambda, boolean normaliseScores, int topK) {
        super(searcher, topK);

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
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        List<MsMarcoQuery> knnQueries = null;
        double variantSpec = 0;

        if (norlamiseScores)
            topDocs = normaliseScores(topDocs);

        try {
            if (numVariants > 0)
                knnQueries = knnRelModel.getKNNs(q, numVariants);

            if (knnQueries!=null && !knnQueries.isEmpty()) {
                variantSpec = variantSpecificity(q, knnQueries, topDocs, topK);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }

        return knnQueries!=null?
                lambda * variantSpec + (1-lambda) * baseModel.computeSpecificity(q, topDocs):
                baseModel.computeSpecificity(q, topDocs);
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

            variantSpecScore = baseModel.computeSpecificity(rq, topDocsRQ);
            refSim = rq.getRefSim();

            specScore +=  refSim * variantSpecScore ;
            z += refSim;
        }

        return z==0? baseModel.computeSpecificity(q, topDocs): specScore/z;
    }

    @Override
    public String name() {
        return String.format("QV-JM-%s-%d-%.2f-k%d", baseModel.name(), numVariants, lambda, topK);
    }
}
