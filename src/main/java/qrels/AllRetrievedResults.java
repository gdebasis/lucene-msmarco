package qrels;
import experiments.Settings;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import retrieval.Constants;

import java.io.*;
import java.util.*;

public class AllRetrievedResults {
    Map<String, RetrievedResults> allRetMap;
    String resFile;
    AllRelRcds allRelInfo;
    int zeroIndexedAdjustment;

    public AllRetrievedResults(String resFile) {
        String line;
        this.resFile = resFile;

        allRetMap = new TreeMap<>();
        try (FileReader fr = new FileReader(resFile);
             BufferedReader br = new BufferedReader(fr); ) {
            while ((line = br.readLine()) != null) {
                storeRetRcd(line);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    public Set<String> queries() { return this.allRetMap.keySet(); }

    public AllRetrievedResults(String qid, TopDocs topDocs) {
        allRetMap = new TreeMap<>();
        RetrievedResults rr = new RetrievedResults(qid);
        int rank = 1;
        for (ScoreDoc sd: topDocs.scoreDocs) {
            rr.addTuple(Settings.getDocIdFromOffset(sd.doc), rank++, sd.score);
        }
        allRetMap.put(qid, rr);
    }

    public RetrievedResults getRetrievedResultsForQueryId(String qid) {
        return allRetMap.get(qid);
    }

    void storeRetRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        RetrievedResults res = allRetMap.get(qid);
        if (res == null) {
            res = new RetrievedResults(qid);
            allRetMap.put(qid, res);
            int rank = Integer.parseInt(tokens[3]);
            zeroIndexedAdjustment = rank==0? 1: 0;
        }
        if (res.rtuples.size() < Constants.NUM_WANTED)
            res.addTuple(tokens[2],
                    Integer.parseInt(tokens[3]) + zeroIndexedAdjustment,
                    Double.parseDouble(tokens[4]));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            buff.append(res.toString()).append("\n");
        }
        return buff.toString();
    }

    public void fillRelInfo(AllRelRcds relInfo) {
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            PerQueryRelDocs thisRelInfo = relInfo.getRelInfo(String.valueOf(res.qid));
            if (thisRelInfo != null)
                res.fillRelInfo(thisRelInfo);
        }
        this.allRelInfo = relInfo;
    }

    public Map<String, TopDocs> castToTopDocs() {
        Map<String, TopDocs> topDocsMap = new HashMap<>();
        for (RetrievedResults rr: allRetMap.values()) {
            int numret = rr.rtuples.size();
            List<ScoreDoc> scoreDocs = new ArrayList<>();
            for (ResultTuple tuple: rr.rtuples) {
                int docOffset = Settings.getDocOffsetFromId(tuple.docName);
                if (docOffset>0)
                    scoreDocs.add(new ScoreDoc(docOffset, (float)tuple.score));
            }
            ScoreDoc[] scoreDocArray = new ScoreDoc[scoreDocs.size()];
            scoreDocArray = scoreDocs.toArray(scoreDocArray);
            TopDocs topDocs = new TopDocs(new TotalHits(numret, TotalHits.Relation.EQUAL_TO), scoreDocArray);
            topDocsMap.put(rr.qid, topDocs);
        }
        return topDocsMap;
    }

    public double compute(String qid, Metric m) {
        double res = 0;
        RetrievedResults rr = allRetMap.get(qid);
        switch (m) {
            case AP: res = rr.computeAP(); break;
            case P_10: res = rr.precAtTop(10); break;
            case Recall: res = rr.computeRecall(); break;
            case nDCG: res = rr.computeNdcg(Constants.NDCG_CUTOFF);
        }
        return res;
    }

    String computeAll() {
        StringBuffer buff = new StringBuffer();
        float map = 0f;
        float avgRecall = 0f;
        float avgNDCG = 0f;
        float numQueries = (float)allRetMap.size();
        float pAt5 = 0f;

        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            map += res.computeAP();
            pAt5 += res.precAtTop(5);
            avgRecall += res.computeRecall();
            avgNDCG += res.computeNdcg(Constants.NDCG_CUTOFF);
        }

        buff.append("recall:\t").append(avgRecall/(float)allRelInfo.getTotalNumRel()).append("\n");
        buff.append("map:\t").append(map/numQueries).append("\n");
        buff.append("P@5:\t").append(pAt5/numQueries).append("\n");
        buff.append("nDCG@10:\t").append(avgNDCG/numQueries).append("\n");

        return buff.toString();
    }
}
