package qrels;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AllRelRcds {
    String qrelsFile;
    Map<String, PerQueryRelDocs> perQueryRels;
    int totalNumRel;

    public AllRelRcds(String qrelsFile) {
        this.qrelsFile = qrelsFile;
        perQueryRels = new HashMap<>();
        totalNumRel = 0;
        load();
    }

    int getTotalNumRel() {
        if (totalNumRel > 0)
            return totalNumRel;

        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            totalNumRel += perQryRelDocs.relMap.size();
        }
        return totalNumRel;
    }

    private void load() {
        try {
            FileReader fr = new FileReader(qrelsFile);
            BufferedReader br = new BufferedReader(fr);
            String line;

            while ((line = br.readLine()) != null) {
                storeRelRcd(line);
            }
            br.close();
            fr.close();
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    void storeRelRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        PerQueryRelDocs relTuple = perQueryRels.get(qid);
        if (relTuple == null) {
            relTuple = new PerQueryRelDocs(qid);
            perQueryRels.put(qid, relTuple);
        }
        int rel = Integer.parseInt(tokens[3]);
        if (rel > 0)
            relTuple.addTuple(tokens[2], Integer.parseInt(tokens[3]));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            buff.append(e.getKey()).append("\n");
            for (Map.Entry<String, Integer> rel : perQryRelDocs.relMap.entrySet()) {
                String docName = rel.getKey();
                int relVal = rel.getValue();
                buff.append(docName).append(",").append(relVal).append("\t");
            }
            buff.append("\n");
        }
        return buff.toString();
    }

    public PerQueryRelDocs getRelInfo(String qid) {
        return perQueryRels.get(qid);
    }
}
