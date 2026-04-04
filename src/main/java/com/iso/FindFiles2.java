package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class FindFiles2 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] meta = reader.readSectors(1186, 64);
        
        System.out.println("=== Files from Metadata ===");
        
        int count = 0;
        // UDF FileId: tag(2)+ver(2)+checksum(1)+serial(1)+version(2)=8
        // char(1)=9, len(1)=10, icb(16)=26, implUse(2)=28 -> name at 28
        for (int i = 0; i < meta.length - 40 && count < 50; i += 4) {
            int tag = ((meta[i+1]&0xFF)<<8)|(meta[i]&0xFF);
            if (tag == 257) {
                int len = meta[i + 10] & 0xFF;  // length of file id
                
                if (len > 1 && len < 200 && i + 28 + len <= meta.length) {
                    // Check if directory (bit 0 of char at offset 9)
                    boolean isDir = (meta[i + 9] & 1) != 0;
                    
                    try {
                        String name = new String(meta, i + 28, len - 1, 
                            java.nio.charset.StandardCharsets.UTF_16BE);
                        if (!name.trim().isEmpty()) {
                            System.out.println((isDir ? "[DIR] " : "[FILE] ") + name);
                            count++;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        System.out.println("Total: " + count + " items");
    }
}
