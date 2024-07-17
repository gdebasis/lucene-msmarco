package indexing;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import retrieval.Constants;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

public class JSONDataIndexer extends MsMarcoIndexer {

    void indexCollection(InputStream is, IndexWriter writer) throws Exception {
        Document doc;
        String line;
        int docCount = -1;

        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        JSONParser parser = new JSONParser();

        while ((line = br.readLine())!= null) {
            line = line.trim();
            JSONObject jsonLine = (JSONObject)parser.parse(new StringReader(line));

            String id = jsonLine.get("id").toString();
            String content = jsonLine.get("title").toString() + " " + jsonLine.get("plain").toString();

            doc = new Document();
            doc.add(constructIDField(id));
            doc.add(constructContentField(content));

            writer.addDocument(doc);

            if (docCount++ % 10000 == 0) {
                System.out.print(String.format("Indexed %d passages from TREC Fair ranking collection\r", docCount));
            }
        }
        System.out.println();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Required arguments: <collection file> <index output directory>");
            args = new String[2];
            args[0] = Constants.TREC_FAIR_IR_COLL;
            args[1] = Constants.TREC_FAIR_IR_INDEX;
        }

        try {
            System.out.println("Indexing documents from the file " + args[0] + " into " + args[1]);
            JSONDataIndexer indexer = new JSONDataIndexer();
            indexer.indexCollection(args[0], args[1]);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
