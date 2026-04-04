package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class ReadMetadata {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] data = reader.readSectors(288, 1);
        
        if (data != null && data.length >= 240) {
            // Allocation descriptor at offset 216 (176+40 for Extended)
            long adLen = ((data[219]&0xFF)<<24)|((data[218]&0xFF)<<16)|((data[217]&0xFF)<<8)|(data[216]&0xFF);
            long adPos = ((data[223]&0xFF)<<24)|((data[222]&0xFF)<<16)|((data[221]&0xFF)<<8)|(data[220]&0xFF);
            long adPart = ((data[227]&0xFF)<<24)|((data[226]&0xFF)<<16)|((data[225]&0xFF)<<8)|(data[224]&0xFF);
            
            System.out.println("Allocation Descriptor:");
            System.out.println("  Length: " + adLen + " bytes");
            System.out.println("  Position: sector " + adPos);
            System.out.println("  Partition: " + adPart);
            
            // Read metadata from this position
            if (adLen > 0 && adPos > 0) {
                System.out.println("\nReading metadata from sector " + adPos + "...");
                byte[] meta = reader.readSectors(adPos, 32);
                if (meta != null) {
                    System.out.println("Read " + meta.length + " bytes");
                    
                    // Search for FileId entries (tag 257)
                    for (int i = 0; i < meta.length - 40; i += 4) {
                        int tag = ((meta[i+1]&0xFF)<<8)|(meta[i]&0xFF);
                        if (tag == 257) {
                            int nameLen = meta[i + 18] & 0xFF;
                            if (nameLen > 1 && i + 38 + nameLen - 1 <= meta.length) {
                                String name = new String(meta, i + 38, nameLen - 1, 
                                    java.nio.charset.StandardCharsets.UTF_16BE);
                                System.out.println("  Found: " + name);
                            }
                        }
                    }
                }
            }
        }
    }
}
