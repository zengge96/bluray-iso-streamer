package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class TestMeta {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        System.out.println("Partition Start: " + parser.getPartitionStart());
        System.out.println("Partition Length: " + parser.getPartitionLength());
        System.out.println("Metadata Partition Start: " + parser.getMetadataPartitionStart());
        System.out.println("Metadata Partition Length: " + parser.getMetadataPartitionLength());
    }
}
