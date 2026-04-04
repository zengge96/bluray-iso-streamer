package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class DebugFileId2 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] meta = reader.readSectors(1186, 32);
        
        // FileId starts at offset 204
        int base = 204;
        
        System.out.println("FileId structure from offset " + base + ":");
        System.out.println("Offset | Hex    | Value");
        System.out.println("-------|--------|-----------------");
        
        // Print first 50 bytes with labels
        String[] labels = {"Tag(2)", "Ver(2)", "CRC", "Serial", "CRC(2)", "CRC2(2)", 
                           "Char(1)", "Len(1)", "ICB[0-15]", "ImplLen(2)"};
        for (int i = 0; i < 38; i++) {
            String label = (i < labels.length) ? labels[i] : "";
            System.out.printf("%6d | %02X %02X | %s%n", i, meta[base+i]&0xFF, meta[base+i+1]&0xFF, label);
            if (i % 2 == 1) System.out.println();
        }
        
        // Specific values we care about:
        System.out.println("\n=== Key values ===");
        System.out.println("Tag: " + ((meta[base+1]&0xFF)<<8|(meta[base]&0xFF)));
        System.out.println("FileCharacteristics at offset 18: " + (meta[base+18]&0xFF));
        System.out.println("FileIdLength at offset 19: " + (meta[base+19]&0xFF));
        
        int idLen = meta[base+19] & 0xFF;
        if (idLen > 0 && idLen < 100) {
            System.out.println("\nTrying to read file name at offset 38, len=" + idLen);
            try {
                String name = new String(meta, base + 38, Math.min(idLen, 50), 
                    java.nio.charset.StandardCharsets.UTF_16BE);
                System.out.println("Name: [" + name + "]");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
