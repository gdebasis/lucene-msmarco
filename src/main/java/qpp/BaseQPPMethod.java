package qpp;

import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

import java.io.IOException;
import java.util.List;
import java.util.Map;

abstract public class BaseQPPMethod implements QPPMethod {
    public void setDataSource(String dataFile) throws IOException {}
    public void writePermutationMap(List<MsMarcoQuery> queries, Map<String, TopDocs> topDocsMap, int sampleNumber) throws IOException {}
}
