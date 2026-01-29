package utils;

import indexing.MsMarcoIndexer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.QueryBuilder;
import retrieval.Constants;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


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

    public static void collectTerms(Query q, Set<Term> out) {
        try {
            if (q instanceof TermQuery) {
                out.add(((TermQuery) q).getTerm());
            } else if (q instanceof BooleanQuery) {
                for (BooleanClause c : ((BooleanQuery) q).clauses()) {
                    collectTerms(c.getQuery(), out);
                }
            } else if (q instanceof PhraseQuery) {
                for (Term t : ((PhraseQuery) q).getTerms()) {
                    out.add(t);
                }
            } else {
                // other query types: skip or try rewriting further
                Query rew = q.rewrite(reader);  // need an IndexReader in scope
                if (rew != q) {
                    collectTerms(rew, out);
                }
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    static Query makeQuery(String qText) {
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        String[] tokens = MsMarcoIndexer
                .analyze(MsMarcoIndexer.constructAnalyzer(), qText).split("\\s+");
        for (String token: tokens) {
            TermQuery tq = new TermQuery(new Term(Constants.CONTENT_FIELD, token));
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        return qb.build();
    }

    public static void main(String[] args) {
        Query q = makeQuery("Lucene is cool");
        Set<Term> terms = new HashSet<>();
        collectTerms(q, terms);
        terms.stream().forEach(System.out::println);
    }
}
