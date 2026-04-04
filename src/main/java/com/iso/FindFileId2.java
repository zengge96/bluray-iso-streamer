package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class FindFileId2 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read metadata partition (832 sectors = 1.7MB)
        for (int sec = 320; sec < 350; sec++) {
            byte[] data = reader.readSectors(sec, 1);
            if (data == null || data.length < 2048) continue;
            
            // Look for FileId tag (257) with name
            for (int i = 0; i < 2048 - 50; i += 4) {
                int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
                if (tag == 257) {
                    int len = data[i + 19] & 0xFF;
                    if (len > 2 && len < 100 && i + 38 + len <= data.length) {
                        boolean isDir = (data[i + 18] & 1) != 0;
                        try {
                            String name = new String(data, i + 38, Math.min(len-1, 40), java.nio.charset.StandardCharsets.UTF_16BE);
                            name = name.replace("\u0000", "");
                            if (name.length() > 1 && name.matches(".*[A-Za-z].*")) {
                                System.out.println("Sector " + sec + " offset " + i + ": " + (isDir?"[DIR] ":"[FILE] ") + name);
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        }
    }
}
