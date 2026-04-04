package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class TestM2TS {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Try sector 4522051 - looks like m2ts data
        byte[] data = reader.readSectors(4522051, 1);
        
        // Check for m2ts sync bytes (0x47 at start of 188-byte or 192-byte packets)
        int m2tsCount = 0;
        for (int i = 0; i < 2000; i++) {
            if ((data[i] & 0xFF) == 0x47) {
                m2tsCount++;
                if (m2tsCount <= 3) {
                    System.out.printf("m2ts sync at offset %d: %02X %02X %02X %02X%n", 
                        i, data[i]&0xFF, data[i+1]&0xFF, data[i+2]&0xFF, data[i+3]&0xFF);
                }
            }
        }
        System.out.println("Total m2ts sync bytes found: " + m2tsCount);
    }
}
