package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class DumpFiles {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] data = reader.readSectors(1474, 32);
        
        // Find and dump first FileId
        for (int i = 0; i < data.length - 40; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag == 257) {
                System.out.println("=== FileId at offset " + i + " ===");
                for (int j = 0; j < 50; j++) {
                    System.out.printf("%02X ", data[i + j] & 0xFF);
                    if ((j+1) % 16 == 0) System.out.println();
                }
                
                // Print char/len values
                System.out.println("\nChar=" + (data[i+18]&0xFF) + " Len=" + (data[i+19]&0xFF));
                
                // Try reading as different encodings
                int len = data[i+19] & 0xFF;
                if (len > 1 && i + 38 + len <= data.length) {
                    System.out.println("\nUTF-16BE: " + new String(data, i+38, Math.min(len,30), java.nio.charset.StandardCharsets.UTF_16BE));
                    System.out.println("UTF-8: " + new String(data, i+38, Math.min(len,30), java.nio.charset.StandardCharsets.UTF_8).replace("\u0000", ""));
                    System.out.println("ASCII: " + new String(data, i+38, Math.min(len,30), java.nio.charset.StandardCharsets.US_ASCII).replace("\u0000", ""));
                }
                break;
            }
        }
    }
}
