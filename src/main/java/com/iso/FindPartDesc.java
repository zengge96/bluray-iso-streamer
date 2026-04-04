package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class FindPartDesc {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Search VDS for partition descriptors
        for (int sec = 32; sec < 48; sec++) {
            byte[] data = reader.readSectors(sec, 1);
            if (data == null) continue;
            
            int tag = ((data[1]&0xFF)<<8)|(data[0]&0xFF);
            if (tag == 5) {  // Partition Descriptor
                System.out.println("Partition Descriptor at sector " + sec);
                String contents = new String(data, 56, 32, "ISO-8859-1").trim();
                System.out.println("  Volume contents: " + contents);
                
                long partStart = ((data[191]&0xFF)<<24)|((data[190]&0xFF)<<16)|((data[189]&0xFF)<<8)|(data[188]&0xFF);
                long partLen = ((data[195]&0xFF)<<24)|((data[194]&0xFF)<<16)|((data[193]&0xFF)<<8)|(data[192]&0xFF);
                System.out.println("  Partition: sector " + partStart + ", len " + partLen);
                
                // Check for metadata partition - often called "Metadata"
                if (contents.contains("Metadata") || contents.contains("META")) {
                    System.out.println("  *** Found Metadata partition! ***");
                }
            }
        }
    }
}
