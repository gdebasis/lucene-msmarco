package qrels;
import java.util.*;
import java.util.stream.Collectors;

public class PerQueryRelDocs {
    String qid;
    Map<String, Integer> relMap; // keyed by docid, entry stores the rel value

    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        relMap = new HashMap();
    }

    public void addTuple(String docId) {
        relMap.put(docId, 1);
    }

    void addTuple(String docId, int rel) {
        if (relMap.get(docId) != null)
            return;
        if (rel > 0) {
            relMap.put(docId, rel);
        }
    }

    public boolean isRel(String docName) {
        return relMap.containsKey(docName);
    }

    public Set<String> getRelDocs() { return relMap.keySet(); }

    public String toString() {
        return relMap.keySet()
                .stream()
                .collect(Collectors.joining(", "));
    }
}
