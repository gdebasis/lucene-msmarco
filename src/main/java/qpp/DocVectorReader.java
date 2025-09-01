package qpp;

import retrieval.Constants;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class DocVectorReader {
    static final int VECTOR_DIM = Constants.VECTOR_DIM;
    private static final int RECORD_SIZE = 4 + VECTOR_DIM * 4; // 4 for docId + 768 floats
    private final RandomAccessFile file;
    private final FileChannel channel;
    private final ByteBuffer buffer;

    public DocVectorReader(String path) throws IOException {
        this.file = new RandomAccessFile(path, "r");
        this.channel = file.getChannel();
        this.buffer = ByteBuffer.allocate(RECORD_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // match Python's struct.pack('<i', ...)
    }

    public float[] getVector(int docId) throws IOException {
        long offset = (long) docId * RECORD_SIZE;

        buffer.clear();
        int bytesRead = channel.read(buffer, offset);
        if (bytesRead != RECORD_SIZE) {
            throw new IOException("Could not read full record for docId: " + docId);
        }

        buffer.flip();
        int readId = buffer.getInt();
        if (readId != docId) {
            throw new IllegalStateException("Expected docId " + docId + ", found " + readId);
        }

        float[] vec = new float[VECTOR_DIM];
        for (int i = 0; i < VECTOR_DIM; i++) {
            vec[i] = buffer.getFloat();
        }

        return vec;
    }

    public void close() throws IOException {
        channel.close();
        file.close();
    }

    // Example usage
    public static void main(String[] args) throws IOException {
        DocVectorReader reader =
                new DocVectorReader(Constants.COLL_DENSEVEC_FILE_CONTRIEVER);

        for (int docId = 1; docId <= 5; docId++) {
            float[] vec = reader.getVector(docId);
            System.out.println("Vector[0:5] for docId " + docId + ": " + Arrays.toString(Arrays.copyOf(vec, 5)));
        }
        reader.close();
    }

}



