package retrieval;

import org.apache.lucene.index.Term;

public class TermWtUtil {
    static public float bm25Weight(float k, float b, int f, int N, int cf, float docLen) {
        return (float)(f*(k+1)/(f+k*(1-b + b* docLen/Constants.FAIRNESS_COLL_AVG_LEN) * bm25IDF(N, cf)));
    }

    static public double bm25IDF(int N, int n) {
        return Math.log(1 + (N-n+.5)/(n+.5));
    }

    static public float tfIdfWeight(int f, int N, int cf) {
        return (float)(f * Math.log(N/(double)cf));
    }

    static public float lmjmWeight(int f, int N, int cf, float docLen, float lambda) {
        return (float)(Math.log(1 + lambda/(1-lambda) * f/docLen * N/(float)cf));
    }
}
