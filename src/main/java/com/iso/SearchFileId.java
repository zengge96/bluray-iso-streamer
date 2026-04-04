package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class SearchFileId {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Search sectors 1400-1600 for FileId
        for (int sec = 1400; sec < 1600; sec += 4) {
            byte[] data = reader.readSectors(sec, 1);
            if (data == null || data.length < 2048) continue;
            
            for (int i = 0; i < 2048 - 40; i += 4) {
                int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
                if (tag == 257) {
                    int len = data[i + 19] & 0xFF;
                    if (len > 1 && len < 50 && i + 38 + len <= data.length) {
                        System.out.println("Found FileId at sector " + sec + ", offset " + i + ", len=" + len);
                        
                        // Try to decode name
                        try {
                            String name = new String(data, i + 38, len - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                            if (name.length() > 1) System.out.println("  Name: " + name);
                        } catch (Exception e) {}
                    }
                }
            }
        }
    }
}
