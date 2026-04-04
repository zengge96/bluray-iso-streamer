package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class GetFiles4368 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read sector 4368 (root directory)
        byte[] data = reader.readSectors(4368, 32);
        
        System.out.println("=== Root Directory at sector 4368 ===");
        
        // Search for FileId (tag 257)
        int count = 0;
        for (int i = 0; i < data.length - 50 && count < 50; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag == 257) {
                int len = data[i + 19] & 0xFF;
                if (len > 1 && len < 100 && i + 38 + len <= data.length) {
                    boolean isDir = (data[i + 18] & 1) != 0;
                    try {
                        String name = new String(data, i + 38, len - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                        if (!name.trim().isEmpty() && !name.contains("\u0000") && !name.matches(".*\\p{C}.*")) {
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
