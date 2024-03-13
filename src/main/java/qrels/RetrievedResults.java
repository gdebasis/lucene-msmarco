package qrels;

import experiments.Settings;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import retrieval.Constants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    public void sortResultTuples() {
        rtuples = rtuples.stream().sorted().collect(Collectors.toList());
    }

    public RetrievedResults(String qid, TopDocs topDocs) {
        this.qid = qid;
        this.rtuples = new ArrayList<>(100);
        int rank = 1;
        for (ScoreDoc sd: topDocs.scoreDocs) {
            addTuple(Settings.getDocIdFromOffset(sd.doc), rank++, sd.score);
        }
        avgP = -1;
        numRelRet = -1;
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

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (ResultTuple rt : rtuples) {
            buff.append(qid).append("\t").
                    append(rt.docName).append("\t").
                    append(rt.rank).append("\t").
                    append(rt.rel).append("\n");
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

    float computeNdcg(int cutoff) {
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
