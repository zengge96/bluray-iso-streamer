package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class ParseFileId {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read sector 323
        byte[] data = reader.readSectors(323, 1);
        
        System.out.println("=== Parsing FileId entries at sector 323 ===");
        
        // Check first entry - tag 257 at offset 0
        // Name length at offset 19, name starts at offset 56
        
        // First entry
        int len1 = data[19] & 0xFF;
        System.out.println("Entry 1: len=" + len1);
        if (len1 > 0 && 56 + len1 <= data.length) {
            String name1 = new String(data, 56, len1 - 1, java.nio.charset.StandardCharsets.UTF_16BE);
            System.out.println("  Name: " + name1);
        }
        
        // Second entry at offset 32
        if (data.length >= 32 + 56) {
            int len2 = data[32 + 19] & 0xFF;
            System.out.println("Entry 2: len=" + len2 + " at offset " + (32+56));
            if (len2 > 0 && 32 + 56 + len2 <= data.length) {
                String name2 = new String(data, 32 + 56, len2 - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                System.out.println("  Name: " + name2);
            }
        }
        
        // Third entry at offset 96
        if (data.length >= 96 + 56) {
            int len3 = data[96 + 19] & 0xFF;
            System.out.println("Entry 3: len=" + len3 + " at offset " + (96+56));
            if (len3 > 0 && 96 + 56 + len3 <= data.length) {
                String name3 = new String(data, 96 + 56, len3 - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                System.out.println("  Name: " + name3);
            }
        }
    }
}
