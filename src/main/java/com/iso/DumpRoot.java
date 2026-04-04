package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class DumpRoot {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] data = reader.readSectors(2048, 1);
        
        System.out.println("First 64 bytes of sector 2048:");
        for (int i = 0; i < 64; i++) {
            System.out.printf("%02X ", data[i] & 0xFF);
            if ((i+1) % 16 == 0) System.out.println();
        }
        
        // Search for any UDF tags
        System.out.println("\nSearching for tags...");
        for (int i = 0; i < 2048 - 16; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag >= 256 && tag <= 266) {
                System.out.println("Tag " + tag + " at offset " + i);
            }
        }
    }
}
