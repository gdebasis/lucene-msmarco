package qpp.subspace;
import java.util.HashMap;
import java.util.Map;

public class MatchVector {
    int docId;

    public MatchVector(int docId) { this.docId = docId; }

    public int getDocId() { return docId; }

    // queryTerm -> BM25 contribution
    private final Map<String, Double> weights = new HashMap<>();

    public void put(String term, double value) {
        weights.put(term, value);
    }

    public double get(String term) {
        return weights.getOrDefault(term, 0.0);
    }

    public Map<String, Double> getWeights() {
        return weights;
    }

    /* ---------- Vector operations ---------- */

    public void add(MatchVector other) {
        for (Map.Entry<String, Double> e : other.weights.entrySet()) {
            weights.merge(e.getKey(), e.getValue(), Double::sum);
        }
    }

    public void scale(double s) {
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            weights.put(e.getKey(), e.getValue() / s);
        }
    }

    public double l1Norm() {
        double sum = 0.0;
        for (double v : weights.values()) {
            sum += Math.abs(v);
        }
        return sum;
    }

    public double l2SquaredDistance(MatchVector other) {
        double sum = 0.0;

        for (String t : weights.keySet()) {
            double d = get(t) - other.get(t);
            sum += d * d;
        }
        for (String t : other.weights.keySet()) {
            if (!weights.containsKey(t)) {
                double d = other.get(t);
                sum += d * d;
            }
        }
        return sum;
    }

    public double cosineSimilarity(MatchVector other) {
        double dot = 0.0, n1 = 0.0, n2 = 0.0;

        for (String t : weights.keySet()) {
            double a = get(t);
            double b = other.get(t);
            dot += a * b;
            n1 += a * a;
        }
        for (double b : other.weights.values()) {
            n2 += b * b;
        }

        if (n1 == 0.0 || n2 == 0.0) return 0.0;
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    public double cosineDistance(MatchVector other) {
        return 1.0 - cosineSimilarity(other);
    }

    @Override
    public String toString() {
        return getDocId() + ": " + weights.toString();
    }
}
