package experiments;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import correlation.KendalCorrelation;
import correlation.MinMaxNormalizer;
import correlation.PearsonCorrelation;
import correlation.QPPCorrelationMetric;
import qrels.*;
import qpp.*;
import retrieval.Constants;
import retrieval.MsMarcoQuery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class QPPEvaluator {

    IndexReader               reader;
    IndexSearcher             searcher;
    int                       numWanted;
    Map<String, TopDocs>      topDocsMap;
    QPPCorrelationMetric      correlationMetric;
    String queryFile;
    String qrelsFile;

    public QPPEvaluator(String queryFile, String qrelsFile, QPPCorrelationMetric correlationMetric, IndexSearcher searcher, int numWanted) {
        this.searcher = searcher;
        this.reader = searcher.getIndexReader();
        this.numWanted = numWanted;
        this.correlationMetric = correlationMetric;
        this.queryFile = queryFile;
        this.qrelsFile = qrelsFile;
    }

    private static List<String> buildStopwordList() {
        List<String> stopwords = new ArrayList<>();
        String line;

        try (FileReader fr = new FileReader("stop.txt");
             BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    public static Analyzer englishAnalyzerWithSmartStopwords() {
        return new EnglishAnalyzer(
                StopFilter.makeStopSet(buildStopwordList())); // default analyzer
    }

    public IndexReader getReader() { return reader; }

    public List<MsMarcoQuery> constructQueries() throws Exception {
        return constructQueries(queryFile);
    }

    public List<MsMarcoQuery> constructQueries(String queryFile) throws Exception {
        Map<String, String> testQueries =
            FileUtils.readLines(new File(queryFile), StandardCharsets.UTF_8)
                    .stream()
                    .map(x -> x.split("\t"))
                    .collect(Collectors.toMap(x -> x[0], x -> x[1])
                    )
        ;

        List<MsMarcoQuery> queries = new ArrayList<>();
        for (Map.Entry<String, String> e : testQueries.entrySet()) {
            String qid = e.getKey();
            String queryText = e.getValue();
            MsMarcoQuery msMarcoQuery = new MsMarcoQuery(qid, queryText, makeQuery(queryText));
            queries.add(msMarcoQuery);
        }
        return queries;
    }

    public Query makeQuery(String queryText) {
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        String[] tokens = Settings.analyze(englishAnalyzerWithSmartStopwords(), queryText).split("\\s+");
        for (String token: tokens) {
            TermQuery tq = new TermQuery(new Term(Constants.CONTENT_FIELD, token));
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        return (Query)qb.build();
    }

    public TopDocs retrieve(MsMarcoQuery query, Similarity sim, int numWanted) throws IOException {
        searcher.setSimilarity(sim);
        return searcher.search(query.getQuery(), numWanted);
    }

    public Evaluator executeQueries(List<MsMarcoQuery> queries, Similarity sim,
                                    int cutoff, String qrelsFile, String resFile,
                                    Map<String, TopDocs> topDocsMap,
                                    Map<String, Integer> maxDepths) throws Exception {
        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (MsMarcoQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim, cutoff);
            if (topDocsMap != null)
                topDocsMap.put(query.getId(), topDocs);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel
        return evaluator;
    }

    public Evaluator executeQueries(List<MsMarcoQuery> queries, Similarity sim,
                                    int cutoff, String qrelsFile, String resFile, 
                                    Map<String, TopDocs> topDocsMap) throws Exception {

        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        FileWriter fw = new FileWriter(resFile);
        BufferedWriter bw = new BufferedWriter(fw);

        for (MsMarcoQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim, cutoff);
            if (topDocsMap != null)
                topDocsMap.put(query.getId(), topDocs);
            saveRetrievedTuples(bw, query, topDocs, sim.toString());
        }
        bw.flush();
        bw.close();
        fw.close();

        Evaluator evaluator = new Evaluator(qrelsFile, resFile); // load ret and rel
        return evaluator;
    }

    public void saveRetrievedTuples(BufferedWriter bw, MsMarcoQuery query,
                                    TopDocs topDocs, String runName) throws Exception {
        StringBuilder buff = new StringBuilder();
        ScoreDoc[] hits = topDocs.scoreDocs;
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            buff.append(query.getId().trim()).append("\tQ0\t").
                    append(d.get(Constants.ID_FIELD)).append("\t").
                    append((i+1)).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\n");
        }
        bw.write(buff.toString());
    }


    double[] evaluate(List<MsMarcoQuery> queries, Similarity sim, Metric m, int cutoff) throws Exception {
        topDocsMap = new HashMap<>();

        int numQueries = queries.size();
        double[] evaluatedMetricValues = new double[numQueries];

        for (MsMarcoQuery query : queries) {
            TopDocs topDocs = retrieve(query, sim, cutoff);
            topDocsMap.put(query.getId(), topDocs);
        }

        Evaluator evaluator = new Evaluator(qrelsFile, Constants.RES_FILE); // load ret and rel

        int i=0;
        for (MsMarcoQuery query : queries) {
            evaluatedMetricValues[i++] = evaluator.compute(query.getId(), m);
        }
        return evaluatedMetricValues;
    }

    public double measureCorrelation(double[] evaluatedMetricValues, double[] qppEstimates) {
        return correlationMetric.correlation(evaluatedMetricValues, qppEstimates);
    }
}
