package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class SearchRealData {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Search sectors 4300-4500 for any valid UDF tags
        System.out.println("Searching sectors 4300-4500...");
        for (int sec = 4300; sec < 4500; sec++) {
            byte[] data = reader.readSectors(sec, 1);
            if (data == null || data.length < 2048) continue;
            
            // Check for valid UDF tag signature (tag id at bytes 0-1, version at 2-3)
            int tag = ((data[1]&0xFF)<<8)|(data[0]&0xFF);
            if (tag >= 256 && tag <= 266) {
                int ver = ((data[3]&0xFF)<<8)|(data[2]&0xFF);
                System.out.println("Sector " + sec + ": tag=" + tag + ", ver=" + ver);
                
                // Check if this looks like real FileId (tag 257 with reasonable length)
                if (tag == 257 && data.length >= 40) {
                    int len = data[19] & 0xFF;
                    System.out.println("  Possibly FileId, len=" + len);
                    if (len > 1 && len < 50) {
                        // Try reading name
                        String name = new String(data, 38, Math.min(len, 20), java.nio.charset.StandardCharsets.UTF_16BE);
                        System.out.println("  Name attempt: [" + name.replace("\u0000", "_") + "]");
                    }
                }
            }
        }
    }
}
