package qpp;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;
import qrels.RetrievedResults;

import java.io.IOException;

public interface QPPMethod {
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int k);
    public String name();
    public void setDataSource(String dataFile) throws IOException;
}


