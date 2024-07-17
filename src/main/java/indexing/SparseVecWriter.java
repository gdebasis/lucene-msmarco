package indexing;

import fdbk.PerDocTermVector;
import fdbk.RetrievedDocTermInfo;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

class TermWt {
    String term;
    float wt;

    float bm25Weight(float k, float b, int tf, int N, int cf, float docLen) {
        double f = (double)tf;
        return (float)(f*(k+1)/(f+k*(1-b + b* docLen/Constants.MSMARCO_PASSAGE_AVG_LEN) * bm25IDF(N, cf)));
    }

    double bm25IDF(int N, int docFreq) {
        double n = (double)docFreq;
        double idf = Math.log(1 + (N-n+.5)/(n+.5));
        return idf;
    }

    TermWt(String term, int tf) {
        this.term = term;
        wt = tf;
    }

    TermWt(String term, int tf, IndexReader reader, int numDocs) throws Exception {
        this.term = term;
        int n = reader.docFreq(new Term(Constants.CONTENT_FIELD, term));
        double idf = Math.log(numDocs/(double)n);
        wt = (float)(tf * idf);
    }

    TermWt(String term, int tf, IndexReader reader, int numDocs, float k, float b, float docLen) throws Exception {
        this.term = term;
        int n = reader.docFreq(new Term(Constants.CONTENT_FIELD, term));
        wt = bm25Weight(k, b, tf, numDocs, n, docLen);
    }

    public String toString() {
        return new StringBuilder().append(term).append(":").append(wt).toString();
    }
}

public class SparseVecWriter {

    public static void writeSparseVecsForAllDocs() throws Exception {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_INDEX).toPath()));
        int numDocs = reader.numDocs();
        Map<String, Integer> word2id = new HashMap<>();
        int termIdInLocalVocab = 0;
        BufferedWriter bw;

        final String OUTDIR = "/Users/debasis/research/common/msmarco/sparsevecs/";
        File dir = new File(OUTDIR);
        if (!dir.exists())
            dir.mkdir();

        for (int i=0; i < numDocs; i++) {
            Document d = reader.document(i);
            String docName = d.get(Constants.ID_FIELD);

            String fileName = String.format("%s/sparse_%s.txt", OUTDIR, docName);
            bw = new BufferedWriter(new FileWriter(fileName));

            PerDocTermVector docvec = OneStepRetriever.buildStatsForSingleDoc(reader, i);
            if (docvec == null) continue;

            for (RetrievedDocTermInfo tinfo: docvec.getPerDocStats().values()) {
                String word = tinfo.getTerm();
                bw.write(String.format("%s:%d", word, tinfo.getTf()));
                bw.newLine();

                Integer termId = word2id.get(word);
                if (termId==null) {
                    word2id.put(word, termIdInLocalVocab++);
                }
            }
            bw.close();

            if (i%10000==0) {
                System.out.println(String.format("Processed %d documents", i));
            }
        }

        word2id = word2id
                .entrySet().stream().sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new))
        ;

        bw = new BufferedWriter(new FileWriter(String.format("%s/vocab.txt", OUTDIR)));
        for (Map.Entry<String, Integer> e: word2id.entrySet()) {
            bw.write(String.format("%s\t%s\n", e.getKey(), e.getValue()));
        }
        bw.close();
    }


    public static void writeSparseVecsForTopDocs(String queryFile) throws Exception {
        OneStepRetriever oneStepRetriever = new OneStepRetriever(queryFile);
        Map<String, TopDocs> results = oneStepRetriever.retrieve();
        IndexReader reader = oneStepRetriever.getSearcher().getIndexReader();
        int numDocs = reader.numDocs();

        final String OUTDIR = "/Users/debasis/research/common/msmarco/topdocvecs/";
        String fileName = String.format("%s/sparse_%s.txt", OUTDIR, new File(queryFile).getName());
        BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));

        for (Map.Entry<String, TopDocs> e : results.entrySet()) {
            String qid = e.getKey();
            String queryText = oneStepRetriever.getQueryMap().get(qid);
            String[] queryTerms = MsMarcoIndexer.analyze(MsMarcoIndexer.constructAnalyzer(), queryText).split("\\s+");

            TopDocs topDocs = results.get(qid);

            // write out query
            StringBuilder queryTermsStr = new StringBuilder();
            queryTermsStr.append(qid);
            queryTermsStr.append("\t");
            for (String queryTerm : queryTerms) {
                TermWt termWt = new TermWt(queryTerm, 1);
                queryTermsStr.append(termWt.toString());
                queryTermsStr.append(" ");
            }

            for (ScoreDoc sd : topDocs.scoreDocs) {
                PerDocTermVector docvec = OneStepRetriever.buildStatsForSingleDoc(reader, sd.doc);
                if (docvec == null) continue;
                int docLen = docvec.getPerDocStats().values().stream().map(x -> x.getTf()).mapToInt(i -> i.intValue()).sum();

                bw.write(queryTermsStr.toString());
                bw.write("\t");

                bw.write(reader.document(sd.doc).get(Constants.ID_FIELD));
                bw.write("\t");

                // write out doc terms
                for (RetrievedDocTermInfo tinfo : docvec.getPerDocStats().values()) {
                    TermWt termWt = new TermWt(tinfo.getTerm(), tinfo.getTf(), reader, numDocs, 1.2f, 0.75f, (float) docLen);
                    bw.write(termWt.toString());
                    bw.write(" ");
                }
                bw.newLine();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        //writeSparseVecsForAllDocs();
        writeSparseVecsForTopDocs("data/trecdl/pass_2019.queries");
        writeSparseVecsForTopDocs("data/trecdl/pass_2020.queries");
    }
}
