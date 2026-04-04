package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class Dump4368 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] data = reader.readSectors(4368, 1);
        
        System.out.println("First 100 bytes of sector 4368:");
        for (int i = 0; i < 100; i++) {
            System.out.printf("%02X ", data[i] & 0xFF);
            if ((i+1) % 20 == 0) System.out.println();
        }
        
        System.out.println("\n\nSearching for any tags 256-266:");
        for (int i = 0; i < 2048 - 16; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag >= 256 && tag <= 266) {
                System.out.printf("Tag %d at offset %d%n", tag, i);
            }
        }
    }
}
