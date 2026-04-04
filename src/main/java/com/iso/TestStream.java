package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class TestStream {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read from main partition start (sector 288)
        byte[] data = reader.readSectors(288, 4);
        
        System.out.println("Read " + data.length + " bytes from sector 288");
        System.out.println("First 32 bytes: ");
        for (int i = 0; i < 32; i++) {
            System.out.printf("%02X ", data[i] & 0xFF);
        }
        
        // Try reading from metadata partition (sector 320)
        byte[] meta = reader.readSectors(320, 1);
        System.out.println("\nRead " + meta.length + " bytes from sector 320");
    }
}
