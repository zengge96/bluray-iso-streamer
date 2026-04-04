package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class GetFiles {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read from sector 1474 (288 + 1186)
        byte[] data = reader.readSectors(1474, 64);
        
        System.out.println("=== Files ===");
        
        int count = 0;
        // FileId: tag(16) + char(1)=18 + len(1)=19 + ICB(16)=20+16=36 + impl(2)=38
        for (int i = 0; i < data.length - 50 && count < 30; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag == 257) {
                int len = data[i + 19] & 0xFF;
                
                if (len > 1 && len < 100 && i + 38 + len <= data.length) {
                    boolean isDir = (data[i + 18] & 1) != 0;
                    
                    try {
                        String name = new String(data, i + 38, len - 1, 
                            java.nio.charset.StandardCharsets.UTF_16BE);
                        if (!name.trim().isEmpty()) {
                            System.out.println((isDir ? "[DIR] " : "[FILE] ") + name);
                            count++;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        System.out.println("Total: " + count);
    }
}
