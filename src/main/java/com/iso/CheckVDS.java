package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class CheckVDS {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        for (int sec = 32; sec < 48; sec++) {
            byte[] data = reader.readSectors(sec, 1);
            if (data == null) continue;
            
            int tag = ((data[1]&0xFF)<<8)|(data[0]&0xFF);
            System.out.println("Sector " + sec + ": tag=" + tag);
        }
    }
}
