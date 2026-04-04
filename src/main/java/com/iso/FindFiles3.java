package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class FindFiles3 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] meta = reader.readSectors(1186, 64);
        
        System.out.println("=== Files from Metadata (7z structure) ===\n");
        
        int count = 0;
        // UDF FileId: Tag(16) + Char(1) at 18 + Len(1) at 19 + ICB(16) at 20 + ImplUse(2) at 36 = 38
        for (int i = 0; i < meta.length - 50 && count < 30; i += 4) {
            int tag = ((meta[i+1]&0xFF)<<8)|(meta[i]&0xFF);
            if (tag == 257) {
                int len = meta[i + 19] & 0xFF;  // File ID length at offset 19
                
                if (len > 1 && len < 200 && i + 38 + len <= meta.length) {
                    boolean isDir = (meta[i + 18] & 1) != 0;  // directory flag at offset 18
                    
                    try {
                        String name = new String(meta, i + 38, len - 1, 
                            java.nio.charset.StandardCharsets.UTF_16BE);
                        if (!name.trim().isEmpty()) {
                            System.out.println((isDir ? "[DIR] " : "[FILE] ") + name);
                            count++;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        System.out.println("\nTotal: " + count + " items");
    }
}
