package experiments;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import correlation.QPPCorrelationMetric;
import qrels.*;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import retrieval.QueryLoader;
import utils.IndexUtils;

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

    public IndexReader getReader() { return reader; }

    public List<MsMarcoQuery> constructQueries() throws Exception {
        return QueryLoader.constructQueries(queryFile);
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
