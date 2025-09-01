package qpp;

import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;
import stochastic_qpp.QPPMetricBundle;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface QPPMethod {
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs);
    public String name();
    public void setDataSource(String dataFile) throws IOException;
    public void writePermutationMap(List<MsMarcoQuery> queries, Map<String, TopDocs> topDocsMap, int sampleNumber) throws IOException;
    public void setMeasure(QPPMetricBundle perf_measure);
    public QPPMetricBundle getMeasure();
}


