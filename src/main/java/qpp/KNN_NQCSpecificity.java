package qpp;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import qrels.ResultTuple;
import retrieval.MsMarcoQuery;
import retrieval.SupervisedRLM;
import retrieval.KNNRelModel;
import qrels.RetrievedResults;
import retrieval.TermDistribution;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KNN_NQCSpecificity extends NQCSpecificity {
    QPPMethod baseModel;
    KNNRelModel knnRelModel;
    int numVariants;
    float lambda, mu;

    public KNN_NQCSpecificity(QPPMethod baseModel,
                              IndexSearcher searcher, KNNRelModel knnRelModel,
                              int numVariants,
                              float lambda, float mu) {
        super(searcher);

        this.baseModel = baseModel;
        this.knnRelModel = knnRelModel;
        this.numVariants = numVariants;
        this.lambda = lambda;
        this.mu = mu;
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        List<MsMarcoQuery> knnQueries = null;
        double variantSpec = 0, colRelSpec = 0;

        try {
            if (numVariants > 0)
                knnQueries = knnRelModel.getKNNs(q, numVariants);

            if (knnQueries != null) {
                int numRelatedQueries = knnQueries.size();
                variantSpec = variantSpecificity(q, knnQueries, retInfo, topDocs, k);
                //colRelSpec = coRelsSpecificity(q, knnQueries.subList(0, Math.min(numNeighbors, numRelatedQueries)), retInfo, topDocs, k);
            }

        }
        catch (Exception ex) { ex.printStackTrace(); }

        //return knnQueries!=null? lambda * variantSpec + (1-lambda) * colRelSpec : baseModel.computeSpecificity(q, retInfo, topDocs, k);
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

        // apply QPP base model on these estimated relevance scores
        for (MsMarcoQuery rq: knnQueries) {
            //System.out.println(rq.toString());

            TopDocs topDocsRQ = searcher.search(rq.getQuery(), k);
            RetrievedResults varInfo = new RetrievedResults(rq.getId(), topDocsRQ);
            //Arrays.stream(varInfo.getRSVs(5)).forEach(System.out::println);

            variantSpecScore = baseModel.computeSpecificity(rq, varInfo, topDocs, k);
            refSim = rq.getRefSim();

            //System.out.println(String.format("%s %.4f", rq.getId(), variantSpecScore));
            specScore +=  refSim * variantSpecScore ;
            z += refSim;
        }

        return z==0? baseModel.computeSpecificity(q, retInfo, topDocs, k): specScore/z;
    }

    double coRelsSpecificity(MsMarcoQuery q, List<MsMarcoQuery> knnQueries,
                             RetrievedResults retInfo, TopDocs topDocs, int k) throws Exception {
        Map<String, Double> knnDocTermWts, thisDocTermWts;
        // TODO-DV: For dense vectors, this will need to work with a different data structure (fixed size arrays)
        knnDocTermWts = knnRelModel.makeAvgLMDocModel(knnQueries); // make a centroid of reldocs for this query

        int i = 1;
        double corelEstimate;
        //double corelEstimateAvg = 0;
        double z_max = retInfo.getTuples().get(0).getScore();

        // apply QPP base model on these estimated relevance scores
        RetrievedResults coRelInfo = new RetrievedResults(q.getId());
        double z_sum = retInfo.getTuples().stream()
                .map(x -> x.getScore())
                .mapToDouble(Double::doubleValue)
                .sum()
        ;

        for (ResultTuple rtuple: retInfo.getTuples()) {
            thisDocTermWts = knnRelModel.makeLMTermWts(rtuple.getDocName(), true);
            corelEstimate = TermDistribution.cosineSim(knnDocTermWts, thisDocTermWts);
            double ret_score = rtuple.getScore();
            coRelInfo.addTuple(rtuple.getDocName(), i++, mu * corelEstimate + (1-mu) * ret_score/z_max);
            //corelEstimateAvg += corelEstimate;
        }
        coRelInfo.sortResultTuples();

        double corelSpec = baseModel.computeSpecificity(q, coRelInfo, topDocs, k);
        return corelSpec;
        //return corelEstimateAvg;
    }

}
