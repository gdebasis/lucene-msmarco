package correlation;

import org.apache.commons.math3.stat.correlation.KendallsCorrelation;


public class KendalCorrelation implements QPPCorrelationMetric {
    @Override
    public double correlation(double[] gt, double[] pred) {
        return new KendallsCorrelation().correlation(gt, pred);
    }
    
    @Override
    public String name() {
        return "tau";
    }
}
