package stochastic_qpp;

import correlation.KendalCorrelation;
import correlation.SARE;

public class TauAndSARE {
    double tau;
    double[] perQuerySARE;

    TauAndSARE(double tau, double[] perQuerySARE) {
        this.perQuerySARE = perQuerySARE;
        this.tau = tau;
    }

    TauAndSARE(double[] gt, double[] pred) {
        tau = new KendalCorrelation().correlation(gt, pred);
        perQuerySARE = new SARE().computeSAREPerQuery(gt, pred);
    }
}
