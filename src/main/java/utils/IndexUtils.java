package utils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import retrieval.Constants;

import java.io.StringReader;
import java.util.HashMap;


public class IndexUtils {
    static IndexReader                       reader;
    static IndexSearcher                     searcher;
    public static int SEED = 314152;
    public static HashMap<String, Integer> docId2OffsetMap = new HashMap<>();
    public static HashMap<Integer, String> offset2DocIdMap = new HashMap<>();

    static public void init(IndexSearcher searcher) {
        reader = searcher.getIndexReader();
        IndexUtils.searcher = searcher;
    }

    public static String getDocIdFromOffset(int docOffset) {
        try {
            String docName = offset2DocIdMap.get(docOffset);
            if (docName == null) {
                docName = reader.document(docOffset).get(Constants.ID_FIELD);
                offset2DocIdMap.put(docOffset, docName);
            }
            return docName;
        }
        catch (Exception ex) { ex.printStackTrace(); }
        return null;
    }

    public static int getDocOffsetFromId(String docId) {
        try {
            Integer offset = docId2OffsetMap.get(docId);
            if (offset == null) {
                Query query = new TermQuery(new Term(Constants.ID_FIELD, docId));
                TopDocs topDocs = searcher.search(query, 1);

                if (topDocs.scoreDocs.length == 0) {
                    System.out.println("Document " + docId + " not found.");
                }
                else {
                    offset = topDocs.scoreDocs[0].doc;
                    docId2OffsetMap.put(docId, offset);
                }
            }
            return offset==null? -1 : offset.intValue();
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
