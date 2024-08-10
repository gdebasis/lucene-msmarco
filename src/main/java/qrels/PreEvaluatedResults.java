package qrels;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class PreEvaluatedResults {
    Map<String, FairnessMetrics> perQueryEvalMap;

    public PreEvaluatedResults(String perQueryMetricsFile) {
        try {
            perQueryEvalMap =
                FileUtils.readLines(new File(perQueryMetricsFile), Charset.defaultCharset())
                .stream()
                .skip(1) // skip header
                .map(x-> new FairnessMetrics(x))
                .collect(Collectors.toMap(x->x.getQid(), x->x));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double compute(String qid, Metric m) {
        FairnessMetrics res = perQueryEvalMap.get(qid);
        if (res == null) return -1;
        return m==Metric.AWRF? res.awrf: m==Metric.nDCG? res.ndcg : res.combined;
    }

    public Set<String> getQueryIds() { return this.perQueryEvalMap.keySet(); }
}
