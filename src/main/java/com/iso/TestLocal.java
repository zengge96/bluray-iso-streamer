package com.iso;
import com.iso.udf.*;
import java.io.*;

public class TestLocal {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_fresh.iso", "r");
        UdfParser parser = new UdfParser(f);
        parser.parse();
        
        System.out.println("Files found: " + parser.getFiles().size());
        for (IsoFile file : parser.getFiles()) {
            System.out.println(file.name + " -> " + file.sector);
        }
        f.close();
    }
}
