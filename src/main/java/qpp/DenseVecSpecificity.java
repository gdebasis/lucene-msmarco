package qpp;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;
import ucar.nc2.util.IO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DenseVecSpecificity extends BaseIDFSpecificity {
    DocVectorReader vecReader;
    Map<Integer, float[]> queryVecs;
    int numTopDocs;

    public DenseVecSpecificity(DocVectorReader vecReader, Map<Integer, float[]> queryVecs, int numTopDocs) {
        this.vecReader = vecReader;
        this.queryVecs = queryVecs;
        this.numTopDocs = numTopDocs;
    }

    @Override
    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs) {
        List<float[]> vecs = new ArrayList<>();
        float[] qvec = queryVecs.get(Integer.parseInt(q.getId()));
        vecs.add(qvec);

        float[] dvec = new float[DocVectorReader.VECTOR_DIM];
        int k = Math.min(numTopDocs, topDocs.scoreDocs.length);

        for (int i=0; i < k; i++) {
            try {
                dvec = vecReader.getVector(topDocs.scoreDocs[i].doc);
            }
            catch (IOException ex) { ex.printStackTrace(); }
            vecs.add(dvec);
        }

        return computeDiameter(vecs);
    }

    public float computeDiameter(List<float[]> vecs) {
        if (vecs == null || vecs.size() < numTopDocs + 1) { // cutoff: 1,2,...k
            throw new IllegalArgumentException("Insufficient vectors: need at least 'cutoff' docs and 1 query vector.");
        }

        int dim = vecs.get(0).length;
        float[] minVals = new float[dim];
        float[] maxVals = new float[dim];

        // Start with the query (first) vector
        float[] queryVec = vecs.get(0);
        System.arraycopy(queryVec, 0, minVals, 0, dim);
        System.arraycopy(queryVec, 0, maxVals, 0, dim);

        // Include first `cutoff` doc vectors
        for (int i = 0; i <= numTopDocs; i++) { // docs start from index 1 (0 is the query which we include in computation)
            float[] dvec = vecs.get(i);
            for (int d = 0; d < dim; d++) {
                minVals[d] = Math.min(minVals[d], dvec[d]);
                maxVals[d] = Math.max(maxVals[d], dvec[d]);
            }
        }

        // Compute the diameter
        float diameter = 0.0f;
        for (int d = 0; d < dim; d++) {
            diameter += maxVals[d] - minVals[d];
        }

        return (float)Math.log(1+1/diameter);
    }

    @Override
    public String name() { return "dense-qpp"; }
}
