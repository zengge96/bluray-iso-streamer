package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class GetFilesFromMeta {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read sector 323 (metadata + 3)
        byte[] data = reader.readSectors(323, 4);
        
        System.out.println("=== Files in Metadata (sector 323) ===");
        
        int count = 0;
        for (int i = 0; i < data.length - 50 && count < 50; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag == 257) {
                int len = data[i + 19] & 0xFF;
                if (len > 2 && len < 100 && i + 38 + len <= data.length) {
                    boolean isDir = (data[i + 18] & 1) != 0;
                    try {
                        String name = new String(data, i + 38, len - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                        name = name.replace("\u0000", "");
                        if (name.length() > 1) {
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
