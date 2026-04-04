package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class CheckMeta {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read metadata from sector 1186
        byte[] meta = reader.readSectors(1186, 32);
        
        System.out.println("Read " + meta.length + " bytes from sector 1186");
        
        // Search for all UDF tags
        for (int i = 0; i < meta.length - 16; i += 4) {
            int tag = ((meta[i+1]&0xFF)<<8)|(meta[i]&0xFF);
            if (tag >= 256 && tag <= 266) {
                int ver = ((meta[i+3]&0xFF)<<8)|(meta[i+2]&0xFF);
                System.out.printf("Tag %d at offset %d, version %d%n", tag, i, ver);
            }
        }
        
        // Also dump first 64 bytes
        System.out.println("\nFirst 64 bytes:");
        for (int i = 0; i < 64; i++) {
            System.out.printf("%02X ", meta[i] & 0xFF);
            if ((i+1) % 16 == 0) System.out.println();
        }
    }
}
