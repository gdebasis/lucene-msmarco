package qpp;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import qrels.PerQueryRelDocs;
import qrels.ResultTuple;
import retrieval.*;
import qrels.RetrievedResults;

import java.util.Arrays;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CoRelSpecificity extends VariantSpecificity {

    public CoRelSpecificity(QPPMethod baseModel,
                            IndexSearcher searcher, KNNRelModel knnRelModel,
                            int numVariants,
                            float lambda, boolean normaliseScores, int k) {
        super(baseModel, searcher, knnRelModel, numVariants, lambda, normaliseScores, k);
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        List<MsMarcoQuery> knnQueries = null;
        double variantSpec = 0, colRelSpec = 0;
        double qppScore = 0;

        try {
            qppScore = lambda * super.computeSpecificity(q, topDocs);
            if (numVariants > 0)
                knnQueries = knnRelModel.getKNNs(q, numVariants);

            if (knnQueries != null) {
                int numRelatedQueries = knnQueries.size();
                colRelSpec = coRelsSpecificity(knnQueries.subList(0, Math.min(numVariants, numRelatedQueries)));
                qppScore += (1-lambda)*colRelSpec;
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }

        return qppScore;
    }

    /*
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
     */

    double coRelsSpecificity(List<MsMarcoQuery> knnQueries) throws Exception {

        int i = 1;
        double corelScore = 0, corelEstimate = 0, refSim;
        double z = 0;

        for (MsMarcoQuery rq: knnQueries) {
            PerQueryRelDocs relDocs = rq.getRelDocSet();
            if (relDocs==null || relDocs.getRelDocs().isEmpty())
                continue;
            String docName = relDocs.getRelDocs().iterator().next();
            String docText = reader.document(knnRelModel.getDocOffset(docName)).get(Constants.CONTENT_FIELD);
            MsMarcoQuery docQuery = new MsMarcoQuery(docName, docText);

            TopDocs topQueries = knnRelModel.getQueryIndexSearcher().search(docQuery.getQuery(), 20);

            if (norlamiseScores)
                topQueries = normaliseScores(topQueries);

            corelEstimate = baseModel.computeSpecificity(rq, null);
            refSim = rq.getRefSim();

            corelScore += refSim * corelEstimate;
            z += refSim;
        }

        return corelScore/z;
    }

}
