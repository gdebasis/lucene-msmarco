package stochastic_qpp;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import retrieval.Constants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class Metadata {
    Map<String, Boolean> genderValueMap  = new HashMap<>();

    public Metadata(String metadataFile) {
        String line;
        JSONParser parser = new JSONParser();
        int count = 0;
        try {
            BufferedReader br = new BufferedReader(new FileReader(metadataFile));
            while ((line = br.readLine())!=null && count++<1000) {
                JSONObject jsonLine = (JSONObject)parser.parse(new StringReader(line));
                String docid = jsonLine.get("page_id").toString();
                String gender = jsonLine.get("gender").toString();
                int len = gender.length();
                gender = gender.substring(0, len-1).substring(1);
                String[] tokens = gender.split(",[ ]*");
                len = gender.length();
                if (len > 0) {
                    gender = gender.substring(0, len - 1).substring(1);
                    boolean male = gender.charAt(0) == 'm';
                    genderValueMap.put(docid, male);
                }
            }
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void showMetadata() {
        this.genderValueMap.entrySet().stream().forEach(System.out::println);
    }

    boolean isMale(String docId) {
        return genderValueMap.get(docId).booleanValue();
    }

    public static void main(String[] args) {
        Metadata metadata = new Metadata(Constants.TREC_FAIR_IR_METADATA);
        metadata.showMetadata();
    }
}
