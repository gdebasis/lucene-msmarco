package qrels;

import retrieval.Constants;

import java.io.FileReader;
import java.util.Properties;


public class Evaluator {
    AllRelRcds relRcds;
    AllRetrievedResults retRcds;

    public Evaluator(String qrelsFile, String resFile) throws Exception {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        fillRelInfo();
    }

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
