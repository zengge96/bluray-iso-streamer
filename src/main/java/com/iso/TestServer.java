package com.iso;
import com.iso.udf.*;
import java.io.*;

public class TestServer {
    public static void main(String[] args) throws Exception {
        // Use local file for quick test
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_fresh.iso", "r");
        UdfParser parser = new UdfParser(f);
        parser.parse();
        
        System.out.println("Files: " + parser.getFiles().size());
        
        // Show first few files
        int count = 0;
        for (IsoFile file : parser.getFiles()) {
            System.out.println(file.name + " -> " + file.sector);
            if (++count > 20) break;
        }
        
        f.close();
    }
}
