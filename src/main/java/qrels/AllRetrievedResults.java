package qrels;
import retrieval.MsMarcoQuery;
import utils.IndexUtils;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;
import retrieval.Constants;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class AllRetrievedResults {
    Map<String, RetrievedResults> allRetMap;
    String resFile;
    AllRelRcds allRelInfo;

    public AllRetrievedResults(String resFile, int numWanted, boolean skipHeader) {
        String line;
        this.resFile = resFile;

        allRetMap = new TreeMap<>();
        try (FileReader fr = new FileReader(resFile); BufferedReader br = new BufferedReader(fr); ) {
            if (skipHeader) br.readLine();
            while ((line = br.readLine()) != null) {
                storeRetRcd(line);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }

        sortResults(numWanted);
    }

    public AllRetrievedResults(String resFile, boolean skipHeader) {
        String line;
        this.resFile = resFile;

        allRetMap = new TreeMap<>();
        try (FileReader fr = new FileReader(resFile); BufferedReader br = new BufferedReader(fr); ) {
            if (skipHeader) br.readLine();
            while ((line = br.readLine()) != null) {
                storeRetRcd(line);
            }
        }
        catch (Exception ex) { ex.printStackTrace(); }

        sortResults();
    }

    public AllRetrievedResults(String resFile, int numWanted) {
        this(resFile, numWanted, false);
    }

    public AllRetrievedResults(String resFile) {
        this(resFile, 1000);
    }

    public void induceScores(IndexReader reader,
        Map<String, MsMarcoQuery> queries,
        Map<String, Float> inducedDocScoreCache) throws Exception {

        for (Map.Entry<String, RetrievedResults> e: allRetMap.entrySet()) {
            String qid = e.getKey();
            MsMarcoQuery query = queries.get(qid);
            //System.out.println("Inducing scores for query " + query.getQueryTerms().toString());
            RetrievedResults retRes = e.getValue();
            retRes.induceScores(reader, query, inducedDocScoreCache);
        }
    }

    private void sortResults() {
        if (!Constants.AUTO_SORT_TOP_DOCS)
            return;

        // This ensures that the res file may not have to be sorted; we sort by the scores
        allRetMap.values().forEach(x->x.sortResultTuples());
        for (RetrievedResults rr: allRetMap.values()) {
            int rank = 1;
            for (ResultTuple tuple: rr.getTuples()) {
                tuple.rank = rank++;
            }
        }
    }

    private void sortResults(int numWanted) {
        if (!Constants.AUTO_SORT_TOP_DOCS)
            return;

        // This ensures that the res file may not have to be sorted; we sort by the scores
        allRetMap.values().forEach(x->x.sortResultTuples(numWanted));
        for (RetrievedResults rr: allRetMap.values()) {
            int rank = 1;
            for (ResultTuple tuple: rr.getTuples()) {
                tuple.rank = rank++;
            }
        }
    }

    public Set<String> queries() { return this.allRetMap.keySet(); }

    public AllRetrievedResults(Map<String, TopDocs> topDocsMap) {
        allRetMap = new TreeMap<>();
        for (Map.Entry<String, TopDocs> e: topDocsMap.entrySet()) {
            String qid = e.getKey();
            TopDocs topDocs = e.getValue();
            RetrievedResults rr = new RetrievedResults(qid);
            int rank = 1;
            for (ScoreDoc sd : topDocs.scoreDocs) {
                rr.addTuple(IndexUtils.getDocIdFromOffset(sd.doc), rank++, sd.score);
            }
            allRetMap.put(qid, rr);
        }
        sortResults();
    }

    public RetrievedResults getRetrievedResultsForQueryId(String qid) {
        return allRetMap.get(qid);
    }

    String storeRetRcd(String line) {
        String[] tokens = line.split("\\s+");

        /* Here we check for two different file types (the third one we leave for a subclass):
        1. RES file --- TREC style 6 column file
        2. Minimalist two column file --- 1st column  QID, 2nd column Doc Name (Rank is the presented order)
        3. Minimalist res file for stochastic ranking ---
        1st column  QID, 2nd column rank number, 3rd column Doc Name (Rank is the presented order)
        */

        String qid = tokens[0];
        RetrievedResults res = allRetMap.get(qid);
        if (res == null) {
            res = new RetrievedResults(qid);
            allRetMap.put(qid, res);
        }

        if (tokens.length >= 6) { // 6 column file
            res.addTuple(tokens[2],
                    Integer.parseInt(tokens[3]), // dummy; we later on assign ranks based on the sorted positions
                    Double.parseDouble(tokens[4])
            );
        }
        else if (tokens.length==2) {
            res.addTuple(tokens[1]); // <QID> <RANK> tuples
        }
        return qid;
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

    public void fillRelInfo(AllRelRcds relInfo, int cutoff) {
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            res.rtuples = res.rtuples.stream().limit(cutoff).collect(Collectors.toList());
            PerQueryRelDocs thisRelInfo = relInfo.getRelInfo(String.valueOf(res.qid));
            if (thisRelInfo != null)
                res.fillRelInfo(thisRelInfo);
        }
        this.allRelInfo = relInfo;
    }


    public TopDocs castToTopDocs(String qid) {
        List<ScoreDoc> scoreDocs = new ArrayList<>();
        RetrievedResults rr = allRetMap.get(qid);
        int numret = rr.rtuples.size();
        for (ResultTuple tuple: rr.rtuples) {
            int docOffset = IndexUtils.getDocOffsetFromId(tuple.docName);
            if (docOffset>0)
                scoreDocs.add(new ScoreDoc(docOffset, (float)tuple.score));
        }
        ScoreDoc[] scoreDocArray = new ScoreDoc[scoreDocs.size()];
        scoreDocArray = scoreDocs.toArray(scoreDocArray);
        TopDocs topDocs = new TopDocs(new TotalHits(numret, TotalHits.Relation.EQUAL_TO), scoreDocArray);
        return topDocs;
    }

    public Map<String, TopDocs> castToTopDocs() {
        Map<String, TopDocs> topDocsMap = new HashMap<>();
        for (RetrievedResults rr: allRetMap.values()) {
            int numret = rr.rtuples.size();
            List<ScoreDoc> scoreDocs = new ArrayList<>();
            for (ResultTuple tuple: rr.rtuples) {
                int docOffset = IndexUtils.getDocOffsetFromId(tuple.docName);
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
            case RR: res = rr.computeRR(); break;
            case AP: res = rr.computeAP(); break;
            case P_10: res = rr.precAtTop(10); break;
            case Recall: res = rr.computeRecall(); break;
            case nDCG: res = rr.computeNdcg(100); break;
            case nDCG_1: res = rr.computeNdcg(1); break;
            case nDCG_10: res = rr.computeNdcg(10); break;
            case nDCG_20: res = rr.computeNdcg(20); break;
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
            avgNDCG += res.computeNdcg(100);
        }

        buff.append("recall:\t").append(avgRecall/(float)allRelInfo.getTotalNumRel()).append("\n");
        buff.append("map:\t").append(map/numQueries).append("\n");
        buff.append("P@5:\t").append(pAt5/numQueries).append("\n");
        buff.append("nDCG@10:\t").append(avgNDCG/numQueries).append("\n");

        return buff.toString();
    }
}
