package qrels;

public class FairnessMetrics {
    String qid;
    float awrf;
    float ndcg;
    float combined;

    public FairnessMetrics(String line) {
        String[] tokens = line.split("\\s+");
        qid = tokens[0];
        awrf = Float.parseFloat(tokens[2]);
        ndcg = Float.parseFloat(tokens[1]);
        combined = awrf * ndcg;
    }

    public String getQid() {
        return qid;
    }
}
