package qpp;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.search.TopDocs;
import retrieval.MsMarcoQuery;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

public class PreComputedPredictor implements QPPMethod {
    String name;
    Map<String, Float> qppMap;

    public PreComputedPredictor(String name) {
        this.name = name;
    }

    public void setDataSource(String dataFile) throws IOException {
        qppMap = new HashMap<>();
        List<String> lines = FileUtils.readLines(new File(dataFile), Charset.defaultCharset());
        for (String line: lines) {
            String[] tokens = line.split("\\s+");
            String qid = tokens[0];
            float qpp_score = Float.parseFloat(tokens[1]);
            qppMap.put(qid, qpp_score);
        }
    }

    public double computeSpecificity(MsMarcoQuery q, TopDocs topDocs, int k) {
        Float qpp_score = qppMap.get(q.getId());
        return qpp_score==null? 0: qpp_score;
    }

    public String name() {
        return name;
    }
}
