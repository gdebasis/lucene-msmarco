package correlation;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;


public class SpearmanCorrelation implements QPPCorrelationMetric {
    @Override
    public double correlation(double[] gt, double[] pred) {
        return new SpearmansCorrelation().correlation(gt, pred);
    }
    
    @Override
    public String name() {
        return "rho";
    }
}
