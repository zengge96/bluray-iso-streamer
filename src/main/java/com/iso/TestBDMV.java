package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class TestBDMV {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // BDMV directory at sector 4456514
        byte[] data = reader.readSectors(4456514, 1);
        
        System.out.println("Read " + data.length + " bytes from sector 4456514 (BDMV)");
        
        // Check for UDF tags in this sector
        for (int i = 0; i < 2048 - 16; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag >= 256 && tag <= 266) {
                System.out.println("Tag " + tag + " at offset " + i);
            }
        }
        
        // Also try reading from sector 4522051 (from ICB for something)
        System.out.println("\nReading sector 4522051:");
        byte[] data2 = reader.readSectors(4522051, 1);
        System.out.println("First 32 bytes: ");
        for (int i = 0; i < 32; i++) {
            System.out.printf("%02X ", data2[i] & 0xFF);
        }
    }
}
