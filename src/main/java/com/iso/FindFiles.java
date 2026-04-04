package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class FindFiles {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] meta = reader.readSectors(1186, 64);  // Read more
        
        System.out.println("=== Searching for files ===");
        
        int count = 0;
        for (int i = 0; i < meta.length - 40 && count < 30; i += 4) {
            int tag = ((meta[i+1]&0xFF)<<8)|(meta[i]&0xFF);
            if (tag == 257) {
                // File ID length is at offset 8 from start of descriptor
                int fileIdLen = meta[i + 8] & 0xFF;
                
                if (fileIdLen > 1 && fileIdLen < 200 && i + 16 + 16 + fileIdLen <= meta.length) {
                    // ICB Tag is at offset 9 (16 bytes)
                    // File name starts at offset 9 + 16 = 25
                    
                    boolean isDir = (meta[i + 10] & 1) != 0;  // flags at offset 10
                    
                    try {
                        // File name at offset 25 for UTF-16BE
                        String name = new String(meta, i + 25, fileIdLen - 1, 
                            java.nio.charset.StandardCharsets.UTF_16BE);
                        if (!name.trim().isEmpty()) {
                            System.out.println((isDir ? "[DIR] " : "[FILE] ") + name);
                            count++;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        System.out.println("Found " + count + " files");
    }
}
