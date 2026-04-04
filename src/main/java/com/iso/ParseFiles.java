package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class ParseFiles {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read metadata from sector 1186
        byte[] meta = reader.readSectors(1186, 32);
        
        System.out.println("=== File List from Metadata ===");
        
        int count = 0;
        // Parse FileId entries (tag 257)
        for (int i = 0; i < meta.length - 40 && count < 50; i += 4) {
            int tag = ((meta[i+1]&0xFF)<<8)|(meta[i]&0xFF);
            if (tag == 257) {
                int len = ((meta[i+3]&0xFF)<<24)|((meta[i+2]&0xFF)<<16)
                    |((meta[i+1]&0xFF)<<8)|(meta[i]&0xFF);
                
                if (len > 38 && i + 38 < meta.length) {
                    // Character set at offset 18
                    // File name length at offset 18
                    int nameLen = meta[i + 18] & 0xFF;
                    
                    if (nameLen > 1 && nameLen < 256 && i + 38 + nameLen <= meta.length) {
                        // Check if it's a directory (bit 0 of flags)
                        boolean isDir = (meta[i + 20] & 1) != 0;
                        
                        // File name starts at offset 38, use UTF-16BE
                        try {
                            String name = new String(meta, i + 38, nameLen - 1, 
                                java.nio.charset.StandardCharsets.UTF_16BE);
                            if (!name.isEmpty()) {
                                System.out.println((isDir ? "[DIR] " : "[FILE] ") + name);
                                count++;
                            }
                        } catch (Exception e) {
                            // Try ASCII
                        }
                    }
                }
            }
        }
        System.out.println("\nTotal: " + count + " items");
    }
}
