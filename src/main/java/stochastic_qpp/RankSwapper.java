package stochastic_qpp;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import qrels.Evaluator;
import qrels.PerQueryRelDocs;
import qrels.ResultTuple;
import qrels.RetrievedResults;
import retrieval.Constants;

import java.util.*;

public class RankSwapper {
    String qid;
    List<TopDocs> permutedTopDocs;

    // Takes a ranked list, gets a rel document, and replaces it with a nonrel doc from
    // a smaller (better) rank
    public RankSwapper(String qid, Evaluator evaluator, TopDocs topDocs) {
        this.qid = qid;
        permutedTopDocs = samplePermutations(qid, evaluator, topDocs);

        /*
        // sanity check
        for (TopDocs permutedTopDocsSample: permutedTopDocs) {
            StochasticQPPEvaluation.compareTopDocs(permutedTopDocsSample, topDocs);
        }
        */

        // debug
        //System.out.println("#permutations: " + permutedTopDocs.size());
    }

    public TopDocs sample() { // sampling with replacement
        int n = permutedTopDocs.size();
        int i = (int)(Math.random()*n);
        TopDocs selected = permutedTopDocs.get(i);
        return selected;
    }

    static public TopDocs shuffle(TopDocs topDocs) {
        int n = topDocs.scoreDocs.length;
        final int numShuffles = 10;

        ScoreDoc[] scoreDocs = new ScoreDoc[topDocs.scoreDocs.length];
        for (int i=0; i < scoreDocs.length; i++) {
            scoreDocs[i] = new ScoreDoc(topDocs.scoreDocs[i].doc, topDocs.scoreDocs[i].score);
        }

        for (int i=0; i<numShuffles; i++) {
            int relRank = (int) (Math.random() * n);
            int nonRelRank = (int) (Math.random() * n);

            // swap a rel doc with a nonrel one --- keep the scores in tact
            /*
            ScoreDoc tmp = new ScoreDoc(scoreDocs[nonRelRank].doc, scoreDocs[nonRelRank].score);
            scoreDocs[nonRelRank] = new ScoreDoc(scoreDocs[relRank].doc, scoreDocs[relRank].score);
            scoreDocs[relRank] = tmp;
             */
            ScoreDoc tmp = new ScoreDoc(scoreDocs[nonRelRank].doc, scoreDocs[nonRelRank].score);
            scoreDocs[nonRelRank] = new ScoreDoc(scoreDocs[relRank].doc, scoreDocs[relRank].score);
            if (!Constants.ALLOW_UNSORTED_TOPDOCS)
                scoreDocs[relRank].doc = tmp.doc;
            else
                scoreDocs[relRank] = tmp;
        }

        TopDocs rerankedDocs = new TopDocs(topDocs.totalHits, scoreDocs);
        return rerankedDocs;
    }

    TopDocs swapRanks(TopDocs topDocs, int relRank, int nonRelRank) {
        ScoreDoc[] scoreDocs = new ScoreDoc[topDocs.scoreDocs.length];
        for (int i=0; i < scoreDocs.length; i++) {
            scoreDocs[i] = new ScoreDoc(topDocs.scoreDocs[i].doc, topDocs.scoreDocs[i].score);
        }

        // swap a rel doc with a nonrel one --- keep the scores in tact
        /*
        ScoreDoc tmp = new ScoreDoc(scoreDocs[nonRelRank].doc, scoreDocs[nonRelRank].score);
        scoreDocs[nonRelRank] = new ScoreDoc(scoreDocs[relRank].doc, scoreDocs[relRank].score);
        scoreDocs[relRank] = tmp;
        */
        ScoreDoc tmp = new ScoreDoc(scoreDocs[nonRelRank].doc, scoreDocs[nonRelRank].score);
        scoreDocs[nonRelRank] = new ScoreDoc(scoreDocs[relRank].doc, scoreDocs[relRank].score);
        if (!Constants.ALLOW_UNSORTED_TOPDOCS)
            scoreDocs[relRank].doc = tmp.doc;
        else
            scoreDocs[relRank] = tmp;

        TopDocs rerankedDocs = new TopDocs(topDocs.totalHits, scoreDocs);
        return rerankedDocs;
    }

    List<TopDocs> samplePermutations(String qid, Evaluator evaluator, TopDocs topDocs) {
        List<Integer> relRanks = new ArrayList<>();
        List<Integer> nrelRanks = new ArrayList<>();
        List<TopDocs> permutedTopDocs = new ArrayList<>();
        permutedTopDocs.add(topDocs);  // make sure that the identity permutation is there to avoid NULL issues

        PerQueryRelDocs relDocs = evaluator.getRelRcds().getRelInfo(qid);

        RetrievedResults retrievedResults = evaluator.getRetrievedResultsForQueryId(qid);
        List<ResultTuple> resultTuples = retrievedResults.getTuples();

        int rank=0;
        for (ResultTuple resultTuple: resultTuples) {
            String docId = resultTuple.getDocName();
            if (relDocs.isRel(docId)) {
                relRanks.add(rank);
            }
            else {
                nrelRanks.add(rank);
            }
            rank++;
        }

        for (int relRank: relRanks) {
            for (int nrelRank: nrelRanks) {
                if (relRank > nrelRank) {
                    permutedTopDocs.add(swapRanks(topDocs, relRank, nrelRank));
                }
            }
        }

        return permutedTopDocs;
    }
}
