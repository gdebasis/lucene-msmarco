package qrels;

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

    public Evaluator(Properties prop) throws Exception {
        this(prop.getProperty("qrels.file"),
                prop.getProperty("res.file")
        );
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
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));

            String qrelsFile = prop.getProperty("qrels.file");
            String resFile = prop.getProperty("res.file");

            Evaluator evaluator = new Evaluator(qrelsFile, resFile);
            System.out.println(evaluator.computeAll());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
