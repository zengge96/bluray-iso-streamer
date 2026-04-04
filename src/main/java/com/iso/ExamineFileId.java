package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class ExamineFileId {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] meta = reader.readSectors(1186, 32);
        
        // First FileId at offset 204
        System.out.println("=== First FileId at offset 204 ===");
        for (int i = 0; i < 80; i++) {
            System.out.printf("%02X ", meta[204 + i] & 0xFF);
            if ((i+1) % 20 == 0) System.out.println();
        }
        
        // Print the tag id from multiple potential offsets
        System.out.println("\n\nTrying different offsets:");
        for (int offset = 200; offset < 250; offset += 4) {
            int tag = ((meta[offset+1]&0xFF)<<8)|(meta[offset]&0xFF);
            if (tag == 257) {
                System.out.println("Found tag 257 at " + offset);
                // Print next 40 bytes
                System.out.println("Data:");
                for (int i = 0; i < 40; i++) {
                    System.out.printf("%02X ", meta[offset + i] & 0xFF);
                    if ((i+1) % 16 == 0) System.out.println();
                }
                
                // Try different name offsets
                System.out.println("\nTrying name offsets:");
                for (int no = 20; no < 35; no++) {
                    int len = meta[offset + no] & 0xFF;
                    if (len > 1 && len < 50 && offset + no + len <= meta.length) {
                        try {
                            String n = new String(meta, offset + no, len, java.nio.charset.StandardCharsets.UTF_16BE);
                            System.out.println("Offset " + no + ", len=" + len + ": [" + n + "]");
                        } catch (Exception e) {}
                    }
                }
            }
        }
    }
}
