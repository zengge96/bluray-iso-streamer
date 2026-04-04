package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class SearchVDS {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read VDS sectors 32-47
        System.out.println("Searching sectors 32-47 for FileSet...");
        for (int sec = 32; sec < 48; sec++) {
            byte[] data = reader.readSectors(sec, 1);
            if (data == null || data.length < 2048) continue;
            
            int tag = ((data[1]&0xFF)<<8)|(data[0]&0xFF);
            System.out.println("Sector " + sec + ": tag=" + tag);
            
            if (tag == 256) {  // FileSet
                System.out.println("  Found FileSet at sector " + sec);
                // RootDir ICB at offset 400
                long rootPos = ((data[403]&0xFF)<<24)|((data[402]&0xFF)<<16)|((data[401]&0xFF)<<8)|(data[400]&0xFF);
                long rootLen = ((data[407]&0xFF)<<24)|((data[406]&0xFF)<<16)|((data[405]&0xFF)<<8)|(data[404]&0xFF);
                System.out.println("  RootDir ICB: sector " + rootPos + ", len " + rootLen);
                break;
            }
        }
    }
}
