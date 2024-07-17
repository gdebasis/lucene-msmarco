package qpp;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;
import qrels.RetrievedResults;

public interface QPPMethod {
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int k);
    public String name();
}


