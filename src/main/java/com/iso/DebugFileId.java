package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class DebugFileId {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] meta = reader.readSectors(1186, 32);
        
        // Look at first FileId at offset 204
        System.out.println("=== FileId at offset 204 ===");
        for (int i = 0; i < 64; i++) {
            System.out.printf("%02X ", meta[204 + i] & 0xFF);
            if ((i+1) % 16 == 0) System.out.println();
        }
        
        // Tag 257 is at bytes 204-205
        int tag = ((meta[205]&0xFF)<<8)|(meta[204]&0xFF);
        System.out.println("\nTag: " + tag);
        
        // Length at 206-207
        int len = ((meta[207]&0xFF)<<24)|((meta[206]&0xFF)<<16)
            |((meta[205]&0xFF)<<8)|(meta[204]&0xFF);
        System.out.println("Descriptor length: " + len);
        
        // Version at 208-209
        int ver = ((meta[209]&0xFF)<<8)|(meta[208]&0xFF);
        System.out.println("Version: " + ver);
        
        //flags at 210-211
        int flags = ((meta[211]&0xFF)<<8)|(meta[210]&0xFF);
        System.out.println("Flags: " + flags);
        
        // File ID length at offset 18 from tag content start
        // Tag content starts at offset 16 (after tag+len+ver)
        int fileIdLen = meta[204 + 16 + 18] & 0xFF;
        System.out.println("File ID length: " + fileIdLen);
        
        // Character set
        int charset = meta[204 + 16 + 17] & 0xFF;
        System.out.println("Character set: " + charset);
        
        // File name starts at offset 16 + 18
        if (fileIdLen > 1) {
            try {
                String name = new String(meta, 204 + 16 + 18, fileIdLen - 1, 
                    java.nio.charset.StandardCharsets.UTF_16BE);
                System.out.println("Name: [" + name + "]");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
