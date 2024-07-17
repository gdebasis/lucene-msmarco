package indexing;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import retrieval.Constants;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class MsMarcoIndexer {

    static public Analyzer constructAnalyzer(String analyzerName) {
        try {
            if (!analyzerName.equalsIgnoreCase("english")) {
                return new WhitespaceAnalyzer();
            }
            else {
                return new
                        EnglishAnalyzer(
                        StopFilter.makeStopSet(
                                FileUtils.readLines(new File(Constants.STOP_FILE), StandardCharsets.UTF_8))
                );
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    static public Analyzer constructAnalyzer() {
        return constructAnalyzer("english");
    }

    void indexCollection(String collFile, String indexDir) throws Exception {
        if (new File(indexDir).listFiles().length > 0) {
            System.err.println("Index already exists at " + indexDir);
            return; // don't overwrite anything
        }

        IndexWriter writer;
        IndexWriterConfig iwcfg = new IndexWriterConfig(constructAnalyzer());
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        writer = new IndexWriter(FSDirectory.open(new File(indexDir).toPath()), iwcfg);
        indexCollection(new FileInputStream(new File(collFile)), writer);
        writer.close();
    }

    static public String normalizeNumbers(String content) {
        return content.replaceAll("(-)?\\d+(\\.\\d*)?", " _NUM_ ");
    }

    // MSMARCO format... docid, text
    void indexCollection(InputStream is, IndexWriter writer) throws Exception {
        Document doc;
        String line;
        int docCount = -1;

        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

        while ((line = br.readLine())!= null) {
            line = line.trim();
            String[] parts = line.split("\t");
            if (parts.length >= 1) {
                String docId = parts[0];
                // normalize the number tokens (the values themselves are not important)
                String content = normalizeNumbers(parts[1]);

                doc = constructDoc(docId, content);
                writer.addDocument(doc);

                if (docCount++ % 10000 == 0)
                    System.out.print(String.format("Indexed %d passages from MSMARCO\r", docCount-1));
            }
        }
        System.out.println();
    }

    Document constructDoc(String id, String content) throws IOException {
        Field idField = constructIDField(id);
        Field contentField = constructContentField(content);

        Document doc = new Document();
        doc.add(idField);
        doc.add(contentField);
        return doc;
    }

    static Field constructIDField(String id) {
        FieldType fieldType = new FieldType();
        fieldType.setIndexOptions(IndexOptions.DOCS);
        fieldType.setStored(true);    // default = false (same as Field.Store.NO)
        fieldType.setTokenized(false);  // default = true (tokenize the content)
        fieldType.setOmitNorms(false); // default = false (used when scoring)
        Field idField = new Field(Constants.ID_FIELD, id, fieldType);
        return idField;
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

    static Field constructContentField(String content) {
        FieldType contentFieldType = new FieldType();
        contentFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        contentFieldType.setStored(true);    // default = false (same as Field.Store.NO)
        contentFieldType.setTokenized(true);  // default = true (tokenize the content)
        contentFieldType.setOmitNorms(false); // default = false (used when scoring)
        contentFieldType.setStoreTermVectors(true);
        Field contentField = new Field(Constants.CONTENT_FIELD, content, contentFieldType);
        return contentField;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Required arguments: <collection file> <index output directory>");
            args = new String[2];
            args[0] = Constants.MSMARCO_COLL;
            args[1] = Constants.MSMARCO_INDEX;
        }

        try {
            System.out.println("Indexing documents from the file " + args[0] + " into " + args[1]);
            MsMarcoIndexer indexer = new MsMarcoIndexer();
            indexer.indexCollection(args[0], args[1]);

            //System.out.println("Indexing queries...");
            // Also index the queries
            //indexer.indexCollection(Constants.QUERY_FILE_TRAIN, Constants.MSMARCO_QUERY_INDEX);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
