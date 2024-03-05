package qpp;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import evaluator.RetrievedResults;
import retrieval.MsMarcoQuery;

public interface QPPMethod {
    public double computeSpecificity(MsMarcoQuery q, RetrievedResults retInfo, TopDocs topDocs, int k);
    public String name();
}


