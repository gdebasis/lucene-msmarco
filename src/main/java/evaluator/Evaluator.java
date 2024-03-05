/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package evaluator;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import experiments.Settings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Debasis
 */

class PerQueryRelDocs {
    String qid;
    Map<String, Integer> relMap; // keyed by docid, entry stores the rel value

    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        relMap = new HashMap<>();
    }

    void addTuple(String docId, int rel) {
        if (relMap.get(docId) != null)
            return;
        if (rel > 0) {
            relMap.put(docId, rel);
        }
    }
}

class AllRelRcds {
    String qrelsFile;
    Map<String, PerQueryRelDocs> perQueryRels;
    int totalNumRel;
    Map<String, Boolean> inducedRel;    // a map from docid to a boolean
                                        // which is true if each system retrieves this doc
                                        // at rank < depth for that query

    public AllRelRcds(String qrelsFile) {
        this.qrelsFile = qrelsFile;
        perQueryRels = new HashMap<>();
        totalNumRel = 0;
        load();
        System.out.println(String.format("Total num rel = %d", perQueryRels.size()));
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
    
    PerQueryRelDocs getRelInfo(String qid) {
        return perQueryRels.get(qid);
    }
}

public class Evaluator {
    AllRelRcds relRcds;
    AllRetrievedResults retRcds;

    public Evaluator(String qrelsFile, String resFile) {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        fillRelInfo();
    }

    public Evaluator(Properties prop) {
        this(prop.getProperty("qrels.file"),
            prop.getProperty("res.file")
        );
    }

    public RetrievedResults getRetrievedResultsForQueryId(String qid) {
        return retRcds.getRetrievedResultsForQueryId(qid);
    }

    private void fillRelInfo() {
        retRcds.fillRelInfo(relRcds);
    }
    
    public String computeAll() {
        return retRcds.computeAll();
    }

    public double compute(String qid, Metric m) {
        return retRcds.compute(qid, m);
    }

    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append(relRcds.toString()).append("\n");
        buff.append(retRcds.toString());
        return buff.toString();
    }

    public float precisionAtDepths(Evaluator ref) {
        int thisNumRels = relRcds.perQueryRels
                .entrySet()
                .stream()
                .map(x->x.getValue().relMap.size())
                .mapToInt(x-> x.intValue())
                .sum();

        int refNumRels = ref.relRcds.perQueryRels
                .entrySet()
                .stream()
                .map(x->x.getValue().relMap.size())
                .mapToInt(x-> x.intValue())
                .sum();
        return thisNumRels/(float)refNumRels;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));
            
            String qrelsFile = prop.getProperty("qrels.file");
            String resFile = prop.getProperty("res.file");
            
            Evaluator evaluator = new Evaluator(qrelsFile, resFile);
            System.out.println(evaluator.computeAll());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
}
