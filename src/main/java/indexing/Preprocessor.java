package indexing;

import org.apache.lucene.analysis.Analyzer;
import retrieval.Constants;

import java.io.*;

public class Preprocessor {
    public static void main(String[] args) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("msmarco.stop.stemmed.tsv"));
            BufferedReader br = new BufferedReader(new FileReader(Constants.MSMARCO_COLL));
            Analyzer analyzer = MsMarcoIndexer.constructAnalyzer();
            String line;
            while ((line = br.readLine()) != null) {
                String[] id_and_text = line.split("\t");
                String analyzed = MsMarcoIndexer.analyze(analyzer, id_and_text[1]);
                bw.write(id_and_text[0]);
                bw.write("\t");
                bw.write(analyzed);
                bw.newLine();
            }
            bw.close();
            br.close();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }

    }
}
