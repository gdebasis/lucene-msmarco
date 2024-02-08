package indexing;

import java.io.*;
import java.util.zip.GZIPInputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class MsMarcoV2JsonToTsvConverter {
    static final String MSMARCO_V2_COLL_ROOT = "/Users/debasis/research/common/msmarco/passages/collv2/";

    private void processDirectory(File dir) throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(MSMARCO_V2_COLL_ROOT + "coll.tsv"));
        File[] files = dir.listFiles();
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                processDirectory(f);  // recurse
            }
            else
                processFile(f, bw);
        }
        bw.close();
    }

    public void processFile(File file, BufferedWriter bw) throws Exception {
        System.out.println("Processing file: " + file.getName());

        InputStream is = new GZIPInputStream(new FileInputStream(file));
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;

        JSONParser parser = new JSONParser();
        int count = 0;

        while ((line = br.readLine())!= null) {
            JSONObject doc = (JSONObject)parser.parse(new StringReader(line));
            bw.write(doc.get("doc_id").toString());
            bw.write("\t");
            String text = doc.get("text").toString().replaceAll("\\n", " ").replaceAll("\\t", " ");
            bw.write(text);
            bw.newLine();
            if (count++ % 10000 == 0)
                System.out.print(String.format("Added %d docs...\r", count));
        }
    }

    public static void main(String[] args) {
        try {
            /*
            String a = "This is a test\t, for crazy \n and \ts within text.\n Can you imagine?";
            a = a.replaceAll("\\n", " ");
            a = a.replaceAll("\\t", " ");
            System.out.println(a);
             */

            MsMarcoV2JsonToTsvConverter msMarcoV2JsonToTsvConverter = new MsMarcoV2JsonToTsvConverter();
            BufferedWriter bw = new BufferedWriter(new FileWriter(MSMARCO_V2_COLL_ROOT + "coll.tsv"));
            msMarcoV2JsonToTsvConverter.processFile(new File(MsMarcoV2JsonToTsvConverter.MSMARCO_V2_COLL_ROOT + "docs.jsonl.gz"), bw);
            bw.close();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
}
