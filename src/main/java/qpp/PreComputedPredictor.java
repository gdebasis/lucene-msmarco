package qpp;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

public class PreComputedPredictor extends BaseQPPMethod {
    String name;
    Map<String, Float> qppMap;
    Map<Integer, Integer> docId2Rank;
    Map<String, TopDocs> pivotTopDocsMap;
    int k;

    final public static String qppScoreFilePrefix = "qpp_precomputed"; // this is a folder name

    public PreComputedPredictor(String name, Map<String, TopDocs> pivotTopDocsMap, int k) {
        this.name = name;
        this.pivotTopDocsMap = pivotTopDocsMap;
        this.k=k;
    }

    public void setPivotTopDocSample(TopDocs pivotTopDocSample) {
        docId2Rank = new HashMap<>();
        int rank=1;
        for (ScoreDoc sd: pivotTopDocSample.scoreDocs) {
            docId2Rank.put(sd.doc, rank++);
        }
    }

    public void setDataSource(String dataFile) throws IOException {
        if (!new File(dataFile).exists())
            return;

        qppMap = new HashMap<>();

        List<String> lines = FileUtils.readLines(new File(dataFile), Charset.defaultCharset());
        for (String line: lines) {
            String[] tokens = line.split("\\s+");
            String qid = tokens[0];
            float qpp_score = Float.parseFloat(tokens[1]);
            qppMap.put(qid, qpp_score);
        }
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        return computeSpecificity(q, topDocs, this.k);
    }

    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int cutoff) {
        int actualK = Math.min(this.k, cutoff);
        Float qpp_score = qppMap.get(q.getId());
        return qpp_score==null? 0: qpp_score;
    }

    public String name() {
        return name+"@k"+k;
    }

    // Dump a permutation map file in the following format:
    // <QID> <Permutation Map String>
    // qpp-model-pm.<i>.tsv --- Python reads from this file
    // 1 1:4,2:7,3:5....
    // 2 1:7,2:10,3:22....
    public void writePermutationMap(List<MsMarcoQuery> queries, Map<String, TopDocs> topDocsMap, int sampleId) throws IOException {

        BufferedWriter bw = new BufferedWriter(new FileWriter(String.format("%s/perm_map.%s.%d.tsv",
                qppScoreFilePrefix, this.name(), sampleId)));

        for (MsMarcoQuery query: queries) {
            StringBuilder sb = new StringBuilder(query.getId() + "\t");

            docId2Rank = new HashMap<>();
            TopDocs pivotTopDocSample = this.pivotTopDocsMap.get(query.getId());
            int rank = 1;
            for (ScoreDoc sd : pivotTopDocSample.scoreDocs) {
                docId2Rank.put(sd.doc, rank++);
            }

            TopDocs permutedSample = topDocsMap.get(query.getId());
            rank = 1;

            for (ScoreDoc sd : permutedSample.scoreDocs) {
                Integer prePermutationRank;
                prePermutationRank = sampleId>0? docId2Rank.get(sd.doc) : rank;
                if (prePermutationRank == null) {
                    System.exit(1); // this CAN't happen!
                }

                sb.append(String.format("%d>%d,", prePermutationRank, rank));
                rank++;
            }
            bw.write(sb.toString());
            bw.newLine();
        }
        bw.close();
    }
}
