package qpp;
import retrieval.Constants;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class QueryVecLoader {
    private static final int VECTOR_DIM = Constants.VECTOR_DIM;
    private static final int RECORD_SIZE = 4 + VECTOR_DIM * 4;  // 4 bytes for int ID + 768 * 4 bytes for float vector

    public static Map<Integer, float[]> load(String path) throws IOException {
        Map<Integer, float[]> embeddingMap = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(path);
             FileChannel channel = fis.getChannel()) {

            long fileSize = channel.size();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            while (buffer.remaining() >= RECORD_SIZE) {
                int docId = buffer.getInt();

                float[] vec = new float[VECTOR_DIM];
                for (int i = 0; i < VECTOR_DIM; i++) {
                    vec[i] = buffer.getFloat();
                }

                embeddingMap.put(docId, vec);
            }
        }
        return embeddingMap;
    }

    public static void main(String[] args) throws IOException {
        Map<Integer, float[]> embeddings = QueryVecLoader.load(Constants.DL20_CONTRIEVER_VECS);

        // Print one example
        embeddings.entrySet().stream().forEach(entry -> {
            System.out.println(entry.getKey() + Arrays.toString(Arrays.copyOf(entry.getValue(), 10)));
        });
    }
}



