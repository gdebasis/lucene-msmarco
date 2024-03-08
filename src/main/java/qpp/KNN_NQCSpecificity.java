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
import java.util.List;
import java.util.Map;

public class KNN_NQCSpecificity extends NQCSpecificity {
    QPPMethod baseModel;
    KNNRelModel knnRelModel;
    int numNeighbors;
    float lambda, mu, residualLinear;

    public KNN_NQCSpecificity(QPPMethod baseModel,
                              IndexSearcher searcher, KNNRelModel knnRelModel,
                              int numNeighbors, float lambda, float mu) {
        super(searcher);

        this.baseModel = baseModel;
        this.knnRelModel = knnRelModel;
        this.numNeighbors = numNeighbors;
        this.lambda = lambda;
        this.mu = mu;
        this.residualLinear = 1-(lambda+mu);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, RetrievedResults retInfo, TopDocs topDocs, int k) {
        List<MsMarcoQuery> knnQueries = null;
        double baseQpp = 0, variantSpec = 0, colRelSpec = 0;

        //System.out.println("Input query: " + q.toString());

        try {
            knnQueries = q.retrieveSimilarQueries(
                    knnRelModel.getAllRels(),
                    knnRelModel.getSearcher(),
                    knnRelModel.getQueryIndexSearcher(),
                    numNeighbors)
            ;

                //knnQueries.stream().forEach(System.out::println);

            baseQpp = baseModel.computeSpecificity(q, retInfo, topDocs, k);
            if (knnQueries != null) {
                variantSpec = variantSpecificity(q, knnQueries, retInfo, topDocs, k);
                colRelSpec = coRelsSpecificity(q, knnQueries, retInfo, topDocs, k);
            }

        }
        catch (Exception ex) { ex.printStackTrace(); }

        return //residualLinear * baseQpp +
                lambda * variantSpec + (1-lambda)*colRelSpec;
    }

    double variantSpecificity(MsMarcoQuery q, List<MsMarcoQuery> knnQueries,
                             RetrievedResults retInfo, TopDocs topDocs, int k) throws Exception {
        double specScore = 0;
        double z = 0;
        // apply QPP base model on these estimated relevance scores
        for (MsMarcoQuery rq: knnQueries) {
            TopDocs topDocsRQ = searcher.search(rq.getQuery(), k);
            RetrievedResults varInfo = new RetrievedResults(rq.getId(), topDocsRQ);
            specScore += rq.getRefSim() * baseModel.computeSpecificity(rq, varInfo, topDocs, k);
            z += rq.getRefSim();
        }

        return z==0? baseModel.computeSpecificity(q, retInfo, topDocs, k): specScore/z;
    }

    double coRelsSpecificity(MsMarcoQuery q, List<MsMarcoQuery> knnQueries,
                             RetrievedResults retInfo, TopDocs topDocs, int k) throws Exception {
        Map<String, Double> knnDocTermWts, thisDocTermWts;
        knnDocTermWts = knnRelModel.makeAvgLMDocModel(knnQueries); // make a centroid of reldocs for this query

        int i = 1;
        double corelEstimate;
        //double corelEstimateAvg = 0;
        double max = retInfo.getTuples().get(0).getScore();

        // apply QPP base model on these estimated relevance scores
        RetrievedResults coRelInfo = new RetrievedResults(q.getId());
        double z = retInfo.getTuples().stream()
                .map(x -> x.getScore())
                .mapToDouble(Double::doubleValue)
                .sum()
        ;

        for (ResultTuple rtuple: retInfo.getTuples()) {
            thisDocTermWts = knnRelModel.makeLMTermWts(rtuple.getDocName(), true);
            corelEstimate = TermDistribution.cosineSim(knnDocTermWts, thisDocTermWts);
            double ret_score = rtuple.getScore();
            coRelInfo.addTuple(rtuple.getDocName(), i++, mu* corelEstimate + (1-mu)*ret_score/z);
            //corelEstimateAvg += corelEstimate;
        }

        double corelSpec = baseModel.computeSpecificity(q, coRelInfo, topDocs, k);
        return corelSpec;
        //return corelEstimateAvg;
    }

}
