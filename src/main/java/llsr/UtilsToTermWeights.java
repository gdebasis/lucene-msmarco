package llsr;

import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import retrieval.OneStepRetriever;
import utils.IndexUtils;

import java.io.*;
import java.util.*;

public class UtilsToTermWeights {
    Map<String, String> queryMap;
    String utilsFile;
    String queryFile;
    String indexDir;
    String outFile;

    IndexReader reader;
    IndexSearcher searcher;

    UtilsToTermWeights(String[] args) throws Exception {
        utilsFile = args[0];
        queryFile = args[1];
        indexDir = args[2];
        outFile = args[3];

        reader = DirectoryReader.open(FSDirectory.open(new File(indexDir).toPath()));
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        IndexUtils.init(searcher);
        IndexUtils.buildVocabulary();

        queryMap = OneStepRetriever.loadQueries(queryFile);
    }

    Set<String> extractQueryTerms(Query query) {
        Set<String> terms = new HashSet<>();

        query.visit(new QueryVisitor() {

            @Override
            public boolean acceptField(String field) {
                return field.equals(Constants.CONTENT_FIELD);
            }

            @Override
            public void consumeTerms(Query q, Term... queryTerms) {
                for (Term t : queryTerms) {
                    terms.add(t.text());
                }
            }
        });

        return terms;
    }

    float computeBM25TermScore(int tf, int df, int docLen) {
        float k1 = 1.2f;
        float b = 0.75f;

        float idf = (float) Math.log(1.0 + (IndexUtils.N - df + 0.5) / (df + 0.5));

        float denom = tf + k1 * (1 - b + b * docLen / IndexUtils.avgdl);
        float tfWeight = tf * (k1 + 1) / denom;

        return idf * tfWeight;
    }

    List<String> processQuery(
            String queryId,
            List<ScoreDoc> scoreDocs) throws IOException {

        List<String> lines = new ArrayList<>();

        String queryText = queryMap.get(queryId);
        if (queryText == null)
            return lines;

        MsMarcoQuery query = new MsMarcoQuery(queryId, queryText);
        Query luceneQuery = query.getQuery();
        //System.out.println("Query: " + queryText);

        Set<String> queryTerms = extractQueryTerms(luceneQuery);
        //System.out.println(queryTerms);

        for (ScoreDoc sd : scoreDocs) {
            int docId = sd.doc;
            String text = reader.document(docId).get(Constants.CONTENT_FIELD);
            float utility = sd.score;   // U(q,d)

            Terms tv = reader.getTermVector(docId, Constants.CONTENT_FIELD);
            if (tv == null)
                continue;

            TermsEnum te = tv.iterator();
            // ---------- FIRST PASS: compute document length ----------
            int docLen = 0;
            BytesRef term;

            while ((term = te.next()) != null) {
                PostingsEnum pe = te.postings(null);
                pe.nextDoc();
                docLen += pe.freq();
            }

            if (docLen == 0) {
                continue;
            }

            Map<Integer, Float> featureMap = new TreeMap<>();
            // ---------- SECOND PASS: iterate over query terms ----------
            te = tv.iterator();  // reset iterator

            for (String qTerm : queryTerms) {
                BytesRef br = new BytesRef(qTerm);
                if (te.seekExact(br)) {
                    PostingsEnum pe = te.postings(null);
                    pe.nextDoc();
                    int tf = pe.freq();
                    int df = reader.docFreq(
                            new Term(Constants.CONTENT_FIELD, qTerm));
                    float termScore = computeBM25TermScore(tf, df, docLen);

                    //System.out.println(
                    //        String.format("Score computed for term |%s| and document %s (%d)",
                    //                qTerm, IndexUtils.getDocIdFromOffset(docId), docId));

                    Integer featureId = IndexUtils.termToFeatureId.get(qTerm);
                    if (featureId != null) {
                        featureMap.put(featureId, termScore);
                    }
                }
            }
            if (featureMap.isEmpty())
                continue;

            // ---------- BUILD LIBSVM LINE ----------
            StringBuilder sb = new StringBuilder();
            sb.append(utility);
            for (Map.Entry<Integer, Float> entry : featureMap.entrySet()) {
                sb.append(" ")
                        .append(entry.getKey())
                        .append(":")
                        .append(entry.getValue());
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    void writeLines(BufferedWriter bw, List<String> lines) throws Exception {
        for (String line: lines) {
            bw.write(line);
            bw.newLine();
        }
    }

    void process() throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
        int numQueries = 0;

        String line;
        List<ScoreDoc> sdList = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(utilsFile));
        line = br.readLine(); // ignore the header line
        String queryId = null, prevQuery = null;

        while ((line = br.readLine()) != null) {
            String[] fields = line.split("\t");
            queryId = fields[0];

            if (prevQuery!=null && !prevQuery.equals(queryId)) {
                numQueries++;
                writeLines(bw, processQuery(prevQuery, sdList));
                sdList.clear();
            }

            String docName = fields[1];
            //System.out.println("Doc name: " + docName);

            int docId = IndexUtils.getDocOffsetFromId(docName);
            //System.out.println("Doc text: " + reader.document(docId).get(Constants.CONTENT_FIELD));

            float utilValue = Float.parseFloat(fields[8]);
            sdList.add(new ScoreDoc(docId, utilValue));

            prevQuery = queryId;
        }

        // process the final batch
        writeLines(bw, processQuery(prevQuery, sdList));
        bw.close();
        numQueries++;
        System.out.println("Processed " + numQueries + " queries");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("usage: java UtilsToTermWeights <utils file> <query file> <index dir> <output file>");
            System.out.println("Using default arguments...");
            String BASEPATH = "/Users/debasis/research/llsr/";
            args = new String[4];
            args[0] = BASEPATH + "utils.small";
            args[1] = BASEPATH + "queries/hotpotqa_train_dpr.tsv";
            args[2] = BASEPATH + "index";
            args[3] = BASEPATH + "termwts.txt";
        }

        UtilsToTermWeights utilsToTermWeights = new UtilsToTermWeights(args);
        utilsToTermWeights.process();
    }
}

