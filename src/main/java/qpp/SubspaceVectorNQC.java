package qpp;

import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import retrieval.Constants;
import retrieval.MsMarcoQuery;
import qpp.subspace.MatchVector;
import utils.IndexUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Subspace-based QPP using true per-term match vectors m_{q,d}
 * computed by a lexical Similarity (e.g., BM25).
 */
public class SubspaceVectorNQC extends NQCSpecificity {

    protected Similarity similarity;
    protected int maxRandomSubspaces = 20;
    protected Random rnd = new Random(42);
    boolean oneDimSubspaceOnly;

    public SubspaceVectorNQC(IndexSearcher searcher,
                             Similarity similarity,
                             int k, boolean oneDimSubspaceOnly) {
        super(searcher, k);
        this.similarity = similarity;
        this.oneDimSubspaceOnly = oneDimSubspaceOnly;
    }

    @Override
    public double computeSpecificity(MsMarcoQuery mq, TopDocs topDocs) {
        topK = Math.min(topK, topDocs.scoreDocs.length);
        try {
            return computeVectorDispersion(mq.getQuery(), topDocs);
        } catch (IOException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    boolean test(List<MatchVector> matchVectors, TopDocs topDocs, int docIndex, List<Term> qTerms) {
        double eps = 1e-4;
        MatchVector topVec = matchVectors.get(docIndex);
        double vecScore = topVec.l1Norm();
        double luceneScore = topDocs.scoreDocs[docIndex].score;

        System.out.println("Lucene score: " + luceneScore);
        System.out.println("Vector L1:    " + vecScore);
        System.out.println("Topvec" + topVec);

        return Math.abs(vecScore - luceneScore)
                <= eps * Math.max(1.0, Math.abs(luceneScore));
    }

    protected double computeVectorDispersion(Query query, TopDocs topDocs)
            throws IOException {
        Set<Term> qTermsSet = new HashSet<>();
        IndexUtils.collectTerms(query, qTermsSet);

        // Deterministic order (important!)
        List<Term> qTerms = new ArrayList<>(qTermsSet);
        /*
        String[] queryWords = qTerms.stream()
                .map(Term::text)
                .toArray(String[]::new);

        // Recommended: sort for stability across runs
        qTerms.sort(Comparator.comparing(Term::text));
        */

        int T = qTerms.size();
        if (T == 0)
            return 0.0;

        // Build match vectors for top-k docs
        List<MatchVector> matchVectors =
                buildMatchVectors(qTerms, topDocs);
        //matchVectors.sort(Comparator.comparingDouble(MatchVector::l1Norm).reversed());

        //System.out.println("Test status: " + test(matchVectors, topDocs, 0, qTerms));

        ///*
        System.out.println("Sim scores:");
        for (int i=0; i < topK; i++) {
            System.out.print("(" + topDocs.scoreDocs[i].doc + ", " + topDocs.scoreDocs[i].score + "), ");
        }
        System.out.println();

        System.out.println("Matched vectors");
        for (MatchVector mv: matchVectors) {
            System.out.println(mv + ": L1-norm = " + mv.l1Norm());
        }
        //*/

        MatchVector globalMean = new MatchVector(-1);
        for (MatchVector v : matchVectors) {
            globalMean.add(v);
        }
        globalMean.scale(matchVectors.size());
        System.out.println("Mean vector: " + globalMean);

        //List<int[]> subspaces = buildSubspaces(qTerms, oneDimSubspaceOnly);
        List<Set<String>> subspaces = buildSubspaces(qTerms, oneDimSubspaceOnly);
        System.out.println("####Subspaces:");
        for (Set<String> ss: subspaces)
            System.out.println(ss);

        double totalVar = 0.0;

        for (Set<String> subSpace : subspaces) {
            double subspaceVariance = subspaceVariance(matchVectors, globalMean, subSpace);

            /*
            System.out.println(query.toString());
            System.out.println("Subspace: ");
            System.out.println(subSpace);
            System.out.println("Variance: " + subspaceVariance);
            */
            totalVar += subspaceVariance;
        }

        return totalVar * avgIDF(query) / (double)(subspaces.size() * topDocs.scoreDocs.length);
        //return totalVar * avgIDF(query) / (double)(subspaces.size());
    }

    protected List<MatchVector> buildMatchVectors(List<Term> qTerms,
                                                  TopDocs topDocs)
            throws IOException {

        int k = Math.min(topK, topDocs.scoreDocs.length);

        // One MatchVector per top-k document (same order as topDocs)
        List<MatchVector> vectors = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            vectors.add(new MatchVector(topDocs.scoreDocs[i].doc));
        }

        // Map global docID -> rank in topDocs
        Map<Integer, Integer> docIdToRank = new HashMap<>();
        for (int i = 0; i < k; i++) {
            docIdToRank.put(topDocs.scoreDocs[i].doc, i);
        }

        // For each query term, run an independent TermQuery
        for (Term term : qTerms) {
            Query tq = new TermQuery(term);

            // Search only up to k docs — sufficient for QPP
            TopDocs termResults = searcher.search(tq, 1000);

            // Fill contributions for documents that overlap with original top-k
            for (ScoreDoc sd : termResults.scoreDocs) {
                Integer rank = docIdToRank.get(sd.doc);
                if (rank != null) {
                    vectors.get(rank).put(term.text(), (double) sd.score * 1.0/((double)rank + .01));
                }
            }
            // Documents not retrieved by this TermQuery implicitly get 0.0
        }
        return vectors;
    }

    protected List<MatchVector> buildMatchVectorsFromIndexReader(
            List<Term> qTerms,
            TopDocs topDocs) throws IOException {

        List<MatchVector> vectors = new ArrayList<>();

        for (int i = 0; i < topK; i++)
            vectors.add(new MatchVector(topDocs.scoreDocs[i].doc));

        Map<Integer, Integer> docIdToRank = new HashMap<>();
        for (int i = 0; i < topK; i++) {
            docIdToRank.put(topDocs.scoreDocs[i].doc, i+1);
        }

        IndexReader reader = searcher.getIndexReader();
        CollectionStatistics collectionStats =
                searcher.collectionStatistics(Constants.CONTENT_FIELD);

        int termIndex = 0;
        for (Term t: qTerms) {
            for (LeafReaderContext ctx : reader.leaves()) {
                LeafReader leaf = ctx.reader();
                PostingsEnum pe = leaf.postings(t);

                if (pe == null)
                    continue;

                TermStates termStates =
                        TermStates.build(searcher, t, true);

                int docFreq = termStates.docFreq();
                long totalTermFreq = termStates.totalTermFreq();

                TermStatistics termStats = searcher.termStatistics(t, docFreq, totalTermFreq);

                Similarity.SimScorer scorer =
                        similarity.scorer(1.0f, collectionStats, termStats);

                int doc;
                while ((doc = pe.nextDoc()) != PostingsEnum.NO_MORE_DOCS) {
                    int globalDoc = doc + ctx.docBase;

                    Integer rank = docIdToRank.get(globalDoc);
                    if (rank != null) {
                        int tf = pe.freq();
                        float score = scorer.score(doc, tf);
                        vectors.get(rank).put(t.text(), score);
                    }
                }
            }
            termIndex++;
        }
        return vectors;
    }

    protected MatchVector project(MatchVector v, Set<String> subspace) {
        MatchVector p = new MatchVector(v.getDocId());
        for (String t : subspace) {
            double w = v.get(t);
            if (w != 0.0) {
                p.put(t, w);
            }
        }
        return p;
    }

    protected double subspaceVariance(List<MatchVector> vectors,
                                      MatchVector globalMean,
                                      Set<String> subspace) {

        MatchVector meanS = project(globalMean, subspace);

        double var = 0.0;
        double totalNorm = 0;
        for (MatchVector v : vectors) {
            MatchVector p = project(v, subspace);
            var += p.l2SquaredDistance(meanS) * v.l1Norm();
            totalNorm += v.l1Norm();
            // or: var += p.cosineDistance(meanS);
        }
        return var / totalNorm;
    }

    protected List<Set<String>> buildSubspaces(List<Term> qTerms, boolean oneDimSubspaceOnly) {
        Set<Set<String>> subs = new HashSet<>();
        int T = qTerms.size();

        Set<String> qTermsText = qTerms.stream()
                .map(Term::text)
                .collect(Collectors.toSet())
                ;
        List<String> qTermsTextList = qTerms.stream()
                .map(Term::text)
                .collect(Collectors.toList())
                ;

        /*
        // size-1
        for (Term t : qTerms) {
            subs.add(Set.of(t.text()));
        }
        if (oneDimSubspaceOnly)
            return subs.stream().collect(Collectors.toList());
        */

        // size-2
        /*
        for (int i = 0; i < T; i++) {
            for (int j = i + 1; j < T; j++) {
                subs.add(Set.of(qTerms.get(i).text(), qTerms.get(j).text()));
            }
        }

        */

        // full space
        subs.add(qTermsText);

        // random higher-order
        /*
        if (T > 3) {
            for (int r = 0; r < maxRandomSubspaces; r++) {
                int dim = 3 + rnd.nextInt(T - 2);
                subs.add(sampleRandomSubspace(qTermsTextList, dim));
            }
        }
        */
        return subs.stream().collect(Collectors.toList());
    }

    protected Set<String> sampleRandomSubspace(List<String> qTerms, int dim) {
        Set<String> s = new HashSet<>();
        while (s.size() < dim) {
            s.add(qTerms.get(rnd.nextInt(qTerms.size())));
        }
        return s;
    }
}