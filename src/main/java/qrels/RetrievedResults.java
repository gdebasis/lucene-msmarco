package qrels;

import retrieval.*;
import utils.IndexUtils;
import fdbk.PerDocTermVector;
import fdbk.RetrievedDocTermInfo;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.util.*;
import java.util.stream.Collectors;

public class RetrievedResults implements Comparable<RetrievedResults> {
    String qid;
    List<ResultTuple> rtuples;
    int numRelRet;
    float avgP;
    PerQueryRelDocs relInfo;

    public RetrievedResults(String qid) {
        this.qid = qid;
        this.rtuples = new ArrayList<>(100);
        avgP = -1;
        numRelRet = -1;
    }

    boolean isRel(ResultTuple tuple) { return tuple.rel >= Constants.EVAL_MIN_REL; }

    public void sortResultTuples(int numWanted) {
        rtuples = rtuples.stream().sorted().limit(numWanted).collect(Collectors.toList());
    }

    public void sortResultTuples() {
        rtuples = rtuples.stream().sorted().collect(Collectors.toList());
    }

    public RetrievedResults(String qid, TopDocs topDocs) {
        this.qid = qid;
        this.rtuples = new ArrayList<>(100);
        int rank = 1;
        for (ScoreDoc sd: topDocs.scoreDocs) {
            addTuple(IndexUtils.getDocIdFromOffset(sd.doc), rank++, sd.score);
        }
        avgP = -1;
        numRelRet = -1;
    }

    float induceTermWeight(IndexReader reader, MsMarcoQuery query, int docId) throws Exception {
        int N = reader.numDocs();
        PerDocTermVector docvec = OneStepRetriever.buildStatsForSingleDoc(reader, docId);
        float docLen = (float)(docvec.getPerDocStats().values().stream().map(x -> x.getTf()).mapToInt(i -> i.intValue()).sum());
        float bm25_wt = 0;

        Set<Term> queryTerms = query.getQueryTerms();

        //for (RetrievedDocTermInfo tinfo : docvec.getPerDocStats().values()) {
        for (Term t: queryTerms) {
            int n = reader.docFreq(t);
            RetrievedDocTermInfo tInfo = docvec.getTermStats(t.text());
            if (tInfo==null) continue; // we may not find all the query terms in this document...

            int f = tInfo.getTf();
            //float wt = TermWtUtil.tfIdfWeight(f, N, n);
            float wt = TermWtUtil.lmjmWeight(f, N, n, docLen, 0.2f);
            //float wt = TermWtUtil.bm25Weight(1.2f, 0.75f, f, N, n, docLen);
            //System.out.println(String.format("wt(%s) = %.4f", t.text(), wt));
            bm25_wt += wt;
        }
        return bm25_wt;
    }

    public void induceScores(IndexReader reader, MsMarcoQuery query, Map<String, Float> inducedDocScoreCache) throws Exception {
        for (ResultTuple rTuple: rtuples) {
            String docName = rTuple.getDocName();
            Float wt = inducedDocScoreCache.get(docName + ":" + query.getId());
            if (wt == null) {
                int docId = IndexUtils.getDocOffsetFromId(docName);
                //System.out.print(String.format("Computing BM25 weights for %s\r", docName));
                wt = induceTermWeight(reader, query, docId);
                inducedDocScoreCache.put(docName + ":" + query.getId(), wt);
            }
            rTuple.score = wt;
        }

        sortResultTuples();

        int rank = 1;
        for (ResultTuple rtuple: rtuples) {
            rtuple.rank = rank++;
        }
        //System.out.println("Induced scores:");
        //System.out.println(this.toString());
    }

    public String getQid() { return qid; }

    public int getNumRet() { return rtuples.size(); }

    public List<ResultTuple> getTuples() { return this.rtuples; }

    public double[] getRSVs(int k) {
        return ArrayUtils
                .toPrimitive(rtuples
                        .stream()
                        .map(ResultTuple::getScore)
                        .limit(Math.min(k, rtuples.size()))
                        .collect(Collectors.toList())
                        .toArray(new Double[0]), 0.0);
    }

    public void addTuple(String docName, int rank, double score) {
        rtuples.add(new ResultTuple(docName, rank, score));
    }

    public void addTuple(String docName) {
        rtuples.add(new ResultTuple(docName, 0, 0));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (ResultTuple rt : rtuples) {
            buff.append(qid).append("\t").
                    append(rt.docName).append("\t").
                    append(rt.rank).append("\t").
                    append(String.format("%.4f", rt.score)).append("\n");
        }
        return buff.toString();
    }

    void fillRelInfo(PerQueryRelDocs relInfo) {
        for (ResultTuple rt : rtuples) {
            Integer relIntObj = relInfo.relMap.get(rt.docName);
            rt.rel = relIntObj == null? 0 : relIntObj.intValue();
        }
        this.relInfo = relInfo;
    }

    int getNumRel() {
        int numRel = 0;
        for (Map.Entry<String, Integer> e: relInfo.relMap.entrySet()) {
            if (e.getValue() >= Constants.EVAL_MIN_REL)
                numRel++;
        }
        return numRel;
    }

    float computeAP() {
        if (avgP > -1)
            return avgP;

        float prec = 0;

        int numRel = getNumRel();
        int numRelSeen = 0;

        for (ResultTuple tuple : this.rtuples) {
            if (!isRel(tuple))
                continue;
            numRelSeen++;
            prec += numRelSeen/(float)(tuple.rank);
        }
        numRelRet = numRelSeen;
        prec = numRel==0? 0 : prec/(float)numRel;
        this.avgP = prec;

        return prec;
    }

    float precAtTop(int k) {
        int numRelSeen = 0;
        int numSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (k>0 && numSeen >= k)
                break;
            if (isRel(tuple))
                numRelSeen++;
            numSeen++;
        }
        return numRelSeen/(float)k;
    }

    float computeRecall() {
        if (numRelRet > -1)
            return numRelRet;
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (!isRel(tuple))
                continue;
            numRelSeen++;
        }
        numRelRet = numRelSeen;
        return numRelSeen/(float)relInfo.relMap.size();
    }

    float computeRR() {
        float rr = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel <= 0)
                continue;
            rr = 1/(float)tuple.rank;
        }
        return rr;
    }

    float computeNdcg(int cutoff) {
        /*
        List<Integer> rels =
            rtuples.stream()
                .filter(x->x.rel>=Constants.EVAL_MIN_REL)
                .map(x->x.rel)
                .sorted(Comparator.reverseOrder())  // more relevant at a smaller rank value is ideal
                .limit(cutoff)
                .collect(Collectors.toList()
        );
         */

        List<Integer> rels =
            relInfo.relMap.values()
                .stream()
                .sorted(Comparator.reverseOrder())  // more relevant at a smaller rank value is ideal
                .limit(cutoff)
                .collect(Collectors.toList());

        float idcg = calcDCG(rels);
        if (idcg == 0)
            return 0;

        List<Integer> rets = this.rtuples.stream()
                .limit(cutoff)
                .map(x->x.rel)
                .collect(Collectors.toList());
        float dcg = calcDCG(rets);

        //System.out.println(rels);
        //System.out.println(rets);

        //System.out.println(String.format("%.4f %.4f", dcg, idcg));
        return dcg/idcg;
    }

    double log2(float x) {
        return Math.log(x)/Math.log(2);
    }

    float calcDCG(List<Integer> relLabels) {
        int rank = 1;
        float ndcg = 0;
        for (Integer relLabel: relLabels) {
            ndcg += (float)relLabel.intValue()/log2(rank+1);
            rank++;
        }
        return ndcg;
    }

    @Override
    public int compareTo(RetrievedResults that) {
        return this.qid.compareTo(that.qid);
    }
}
