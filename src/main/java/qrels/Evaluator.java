package qrels;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import retrieval.Constants;
import retrieval.KNNRelModel;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;

import java.util.*;

public class Evaluator {
    AllRelRcds relRcds;
    AllRetrievedResults retRcds;

    public Evaluator(String qrelsFile, String resFile) throws Exception {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        fillRelInfo();
    }

    public Evaluator(String qrelsFile, String resFile, KNNRelModel knnModel) throws Exception {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        fillRelInfo();
        addQueryVariantResults(knnModel);
    }

    void addQueryVariantResults(KNNRelModel knnRelModel) throws Exception {
        for (Map.Entry<String, List<MsMarcoQuery>> e: knnRelModel.getKnnQueryMap().entrySet()) {
            List<MsMarcoQuery> knnQueries = e.getValue();
            for (MsMarcoQuery rq : knnQueries) {
                TopDocs topDocs = knnRelModel.getSearcher().search(rq.getQuery(), Constants.QPP_NUM_TOPK);
                RetrievedResults rr = new RetrievedResults(rq.getId());

                int rank = 1;
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    Document doc = knnRelModel.getSearcher().getIndexReader().document(sd.doc);
                    String docName = doc.get(Constants.ID_FIELD);
                    rr.addTuple(docName, rank++, sd.score);
                }
                retRcds.allRetMap.put(rr.qid, rr);
            }
        }
    }

    public AllRetrievedResults getAllRetrievedResults() { return retRcds; }

    public RetrievedResults getRetrievedResultsForQueryId(String qid) {
        return retRcds.getRetrievedResultsForQueryId(qid);
    }

    void fillRelInfo() {
        retRcds.fillRelInfo(relRcds);
    }

    public String computeAll() {
        return retRcds.computeAll();
    }

    public double compute(String qid, Metric m) {
        return retRcds.compute(qid, m);
    }

    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append(relRcds.toString()).append("\n");
        buff.append(retRcds.toString());
        return buff.toString();
    }

    public static void main(String[] args) {
        try {
            String qrelsFile = Constants.QRELS_TEST;
            String resFile = Constants.RES_FILE;

            Evaluator evaluator = new Evaluator(qrelsFile, resFile);
            System.out.println(evaluator.computeAll());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
