package stochastic_qpp;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.TopDocs;
import qrels.AllRelRcds;
import qrels.AllRetrievedResults;
import qrels.Evaluator;
import qrels.FairnessMetrics;
import retrieval.MsMarcoQuery;
import retrieval.QueryLoader;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreRetrievedStochasticResults {
    Map<String, TopDocs> topDocsMaps[]; // a list of topDocs for the query set
    Evaluator[] permutationEvaluators;
    AllRelRcds relRcds;
    Map<String, MsMarcoQuery> queries;

    public PreRetrievedStochasticResults(IndexReader reader, String resFile, String queryFile,
         Map<String, Float> inducedDocScoreCache, int numRankings, String qrelsFile) throws Exception {

        queries = QueryLoader.constructQueryMap(queryFile);

        relRcds = new AllRelRcds(qrelsFile);

        topDocsMaps = new HashMap[numRankings];
        permutationEvaluators = new Evaluator[numRankings];

        // Load the results from numRankings files
        for (int i=0; i < numRankings; i++) {
            String rankPermResFile = new File(resFile + "." + (i+1)).getPath();
            AllRetrievedResults allRetrievedResults =
                    new AllRetrievedResults(rankPermResFile, false);

            // induce the ranks and scores because here it's a minimalist result file with no score.
            System.out.print(String.format("Inducing scores in %s\r", rankPermResFile));
            allRetrievedResults.induceScores(reader, queries, inducedDocScoreCache);
            topDocsMaps[i] = allRetrievedResults.castToTopDocs();
            permutationEvaluators[i] = new Evaluator(relRcds, topDocsMaps[i]);
        }
        System.out.println();
    }
}
