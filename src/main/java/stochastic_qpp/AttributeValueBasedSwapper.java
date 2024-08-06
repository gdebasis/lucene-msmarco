package stochastic_qpp;

import org.apache.lucene.search.TopDocs;
import qrels.Evaluator;
import qrels.PerQueryRelDocs;
import qrels.ResultTuple;
import qrels.RetrievedResults;

import java.util.ArrayList;
import java.util.List;


public class AttributeValueBasedSwapper extends RankSwapper {
    Metadata metadata;

    public AttributeValueBasedSwapper(String qid, Evaluator evaluator, TopDocs topDocs, Metadata metadata) {
        super(qid, evaluator, topDocs);
        this.metadata = metadata;
    }

    List<TopDocs> samplePermutations(String qid, Evaluator evaluator, TopDocs topDocs) {
        List<Integer> relRanks = new ArrayList<>();
        List<Integer> nrelRanks = new ArrayList<>();
        List<TopDocs> permutedTopDocs = new ArrayList<>();
        permutedTopDocs.add(topDocs);  // make sure that the identity permutation is there to avoid NULL issues

        RetrievedResults retrievedResults = evaluator.getRetrievedResultsForQueryId(qid);
        List<ResultTuple> resultTuples = retrievedResults.getTuples();

        int rank=0;
        for (ResultTuple resultTuple: resultTuples) {
            String docId = resultTuple.getDocName();
            if (metadata.genderValueMap.get(docId) == null)
                continue;
            if (metadata.isMale(docId)) {
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
