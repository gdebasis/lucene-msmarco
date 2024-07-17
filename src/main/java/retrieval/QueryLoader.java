package retrieval;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import utils.IndexUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QueryLoader {

    static public Map<String, MsMarcoQuery> constructQueryMap(String queryFile) throws Exception {
        return
            FileUtils.readLines(new File(queryFile), StandardCharsets.UTF_8)
            .stream()
            .map(x -> x.split("\t"))
            .collect(Collectors.toMap(x -> x[0], x -> new MsMarcoQuery(x[0], x[1])))
        ;
    }

    static public List<MsMarcoQuery> constructQueries(String queryFile) throws Exception {
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

    public static Analyzer englishAnalyzerWithSmartStopwords() {
        return new EnglishAnalyzer(
                StopFilter.makeStopSet(buildStopwordList())); // default analyzer
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

    static public Query makeQuery(String queryText) {
        BooleanQuery.Builder qb = new BooleanQuery.Builder();
        String[] tokens = IndexUtils.analyze(englishAnalyzerWithSmartStopwords(), queryText).split("\\s+");
        for (String token: tokens) {
            TermQuery tq = new TermQuery(new Term(Constants.CONTENT_FIELD, token));
            qb.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
        }
        return (Query)qb.build();
    }

}
