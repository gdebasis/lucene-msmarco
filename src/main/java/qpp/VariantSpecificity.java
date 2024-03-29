package qpp;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import qrels.ResultTuple;
import qrels.RetrievedResults;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.TermDistribution;

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

    RetrievedResults normaliseScores(RetrievedResults retInfo) {
        double minScore = retInfo.getTuples()
                .stream().map(x->x.getScore()).reduce(Double::min).get();
        double maxScore = retInfo.getTuples()
                .stream().map(x->x.getScore()).reduce(Double::max).get();
        double diff = maxScore - minScore;

        if (norlamiseScores) {
            retInfo.getTuples()
                    .forEach(
                            x -> x.setScore((x.getScore()-minScore)/diff)
                    );
        }
        return retInfo;
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        List<MsMarcoQuery> knnQueries = null;
        double variantSpec = 0;

        if (norlamiseScores)
            retInfo = normaliseScores(retInfo);

        try {
            if (numVariants > 0)
                knnQueries = knnRelModel.getKNNs(q, numVariants);

            if (knnQueries!=null && !knnQueries.isEmpty()) {
                variantSpec = variantSpecificity(q, knnQueries, retInfo, topDocs, k);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }

        return knnQueries!=null?
                lambda * variantSpec + (1-lambda) * baseModel.computeSpecificity(q, retInfo, topDocs, k):
                baseModel.computeSpecificity(q, retInfo, topDocs, k);
    }

    double variantSpecificity(MsMarcoQuery q, List<MsMarcoQuery> knnQueries,
                              RetrievedResults retInfo, TopDocs topDocs, int k) throws Exception {
        double specScore = 0;
        double z = 0;
        double variantSpecScore;
        double refSim;

        //System.out.println("orig scores:");
        //retInfo.getTuples().stream().limit(5).forEach(System.out::println);

        // apply QPP base model on these estimated relevance scores
        for (MsMarcoQuery rq: knnQueries) {
            //System.out.println(rq.toString());

            TopDocs topDocsRQ = searcher.search(rq.getQuery(), k);
            RetrievedResults varInfo = new RetrievedResults(rq.getId(), topDocsRQ);

            //System.out.println("var scores:");
            //varInfo.getTuples().stream().limit(5).forEach(System.out::println);

            if (norlamiseScores)
                varInfo = normaliseScores(varInfo);

            //System.out.println("var scores after norm:");
            //varInfo.getTuples().stream().limit(5).forEach(System.out::println);

            variantSpecScore = baseModel.computeSpecificity(rq, varInfo, topDocs, k);
            refSim = rq.getRefSim();

            //System.out.println(String.format("%s %.4f", rq.getId(), variantSpecScore));
            specScore +=  refSim * variantSpecScore ;
            z += refSim;
        }

        return z==0? baseModel.computeSpecificity(q, retInfo, topDocs, k): specScore/z;
    }

}
