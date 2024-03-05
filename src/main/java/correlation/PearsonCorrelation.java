package correlation;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

public class PearsonCorrelation implements QPPCorrelationMetric {
    @Override
    public double correlation(double[] gt, double[] pred) {
        return new PearsonsCorrelation().correlation(gt, pred);
    }
    
    @Override
    public String name() {
        return "r";
    }
}
