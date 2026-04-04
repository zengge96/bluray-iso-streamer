package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class DebugEntry {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        // Create a reader and read sector 288 directly
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] data = reader.readSectors(288, 1);
        
        if (data != null && data.length >= 2048) {
            System.out.println("Read sector 288: " + data.length + " bytes");
            
            // Print hex dump of first 64 bytes
            System.out.println("First 64 bytes:");
            for (int i = 0; i < 64; i++) {
                System.out.printf("%02X ", data[i] & 0xFF);
                if ((i+1) % 16 == 0) System.out.println();
            }
            
            // Check tag at different offsets
            System.out.println("\nTag at offset 0: " + ((data[1]&0xFF)<<8|data[0]&0xFF));
            System.out.println("Tag at offset 16: " + ((data[17]&0xFF)<<8|data[16]&0xFF));
            
            // Check ICB info at offset 36 (tag descriptor)
            // Extended File Entry has ICB at offset 36
            int icbType = data[36 + 11] & 0xFF;  // ICB File Type
            System.out.println("ICB File Type at offset 47: " + icbType);
        }
    }
}
