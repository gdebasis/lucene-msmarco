package stochastic_qpp;

import correlation.KendalCorrelation;
import correlation.SARE;

import java.util.Arrays;

public class TauAndSARE {
    double tau;
    double[] perQuerySARE;

    public TauAndSARE(double tau, double[] perQuerySARE) {
        this.perQuerySARE = perQuerySARE;
        this.tau = tau;
    }

    public TauAndSARE(double[] gt, double[] pred) {
        tau = new KendalCorrelation().correlation(gt, pred);
        perQuerySARE = new SARE().computeSAREPerQuery(gt, pred);
    }

    public double tau() { return tau; }
    public double[] getPerQuerySARE() { return perQuerySARE; }
    public double sare() { return Arrays.stream(perQuerySARE).average().getAsDouble(); }
    public double sarc() { return 1-Arrays.stream(perQuerySARE).average().getAsDouble(); } // 1-sare
}
