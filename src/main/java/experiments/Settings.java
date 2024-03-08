package experiments;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import correlation.*;
import qrels.Metric;
import qpp.*;
import retrieval.Constants;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


public class Settings {
    static IndexReader                       reader;
    static IndexSearcher                     searcher;
    public static String RES_FILE = "/tmp/res";
    public static int SEED = 314152;

    static public void init(IndexSearcher searcher) {
        reader = searcher.getIndexReader();
        Settings.searcher = searcher;
    }

    public static String getDocIdFromOffset(int docOffset) {
        try {
            return reader.document(docOffset).get(Constants.ID_FIELD);
        }
        catch (Exception ex) { ex.printStackTrace(); }
        return null;
    }

    public static int getDocOffsetFromId(String docId) {
        try {
            Query query = new TermQuery(new Term(Constants.ID_FIELD, docId));
            TopDocs topDocs = searcher.search(query, 1);
            return topDocs.scoreDocs[0].doc;
        }
        catch (Exception ex) { ex.printStackTrace(); }
        return -1;
    }

    public static String analyze(Analyzer analyzer, String query) {
        StringBuffer buff = new StringBuffer();
        try {
            TokenStream stream = analyzer.tokenStream("dummy", new StringReader(query));
            CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String term = termAtt.toString();
                buff.append(term).append(" ");
            }
            stream.end();
            stream.close();

            if (buff.length()>0)
                buff.deleteCharAt(buff.length()-1);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

        return buff.toString();
    }
}
