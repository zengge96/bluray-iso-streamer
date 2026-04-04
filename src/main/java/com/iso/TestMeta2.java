package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class TestMeta2 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // From earlier: partition has metadata at sector 1186
        // Read from that position
        byte[] data = reader.readSectors(288 + 1186, 32);
        
        System.out.println("Read " + data.length + " bytes");
        
        // Search for FileSet (256) or FileId (257)
        int found = 0;
        for (int i = 0; i < data.length - 40; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag == 256 || tag == 257) {
                System.out.println("Found tag " + tag + " at offset " + i);
                found++;
                if (found > 5) break;
            }
        }
    }
}
