package indexing;

import fdbk.PerDocTermVector;
import fdbk.RetrievedDocTermInfo;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import retrieval.Constants;
import retrieval.OneStepRetriever;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SparseVecWriter {

    public static void main(String[] args) throws Exception {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Constants.MSMARCO_INDEX).toPath()));
        int numDocs = reader.numDocs();
        Map<String, Integer> word2id = new HashMap<>();
        int termIdInLocalVocab = 0;
        BufferedWriter bw;

        final String OUTDIR = "/Users/debasis/research/common/msmarco/sparsevecs/";
        File dir = new File(OUTDIR);
        if (!dir.exists())
            dir.mkdir();

        for (int i=0; i < numDocs; i++) {
            Document d = reader.document(i);
            String docName = d.get(Constants.ID_FIELD);

            String fileName = String.format("%s/sparse_%s.txt", OUTDIR, docName);
            bw = new BufferedWriter(new FileWriter(fileName));

            PerDocTermVector docvec = OneStepRetriever.buildStatsForSingleDoc(reader, i);
			if (docvec == null) continue;

            for (RetrievedDocTermInfo tinfo: docvec.getPerDocStats().values()) {
                String word = tinfo.getTerm();
                bw.write(String.format("%s:%d", word, tinfo.getTf()));
                bw.newLine();

                Integer termId = word2id.get(word);
                if (termId==null) {
                    word2id.put(word, termIdInLocalVocab++);
                }
            }
            bw.close();

            if (i%10000==0) {
                System.out.println(String.format("Processed %d documents", i));
            }
        }

        word2id = word2id
                .entrySet().stream().sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new))
        ;

        bw = new BufferedWriter(new FileWriter(String.format("%s/vocab.txt", OUTDIR)));
        for (Map.Entry<String, Integer> e: word2id.entrySet()) {
            bw.write(String.format("%s\t%s\n", e.getKey(), e.getValue()));
        }
        bw.close();
    }
}
