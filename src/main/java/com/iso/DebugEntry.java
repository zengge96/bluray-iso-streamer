package com.iso;
import java.io.*;

public class DebugEntry {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);
        byte[] data = new byte[100];
        f.readFully(data);
        
        System.out.println("Bytes 16-50:");
        for (int i = 16; i < 50; i++) {
            System.out.printf("%02X ", data[i] & 0xFF);
            if ((i-15) % 16 == 0) System.out.println();
        }
        
        System.out.println("\n\nAt offset 32 (start of entry 2):");
        f.seek(661504 + 32);
        byte[] ent2 = new byte[60];
        f.readFully(ent2);
        for (int i = 0; i < 60; i++) {
            System.out.printf("%02X ", ent2[i] & 0xFF);
            if ((i+1) % 16 == 0) System.out.println();
        }
        
        // Look for "BDMV" pattern
        System.out.println("\n\nSearching for BDMV...");
        for (int i = 0; i < data.length - 8; i += 2) {
            if (data[i] == 0x42 && data[i+1] == 0x00 && 
                data[i+2] == 0x44 && data[i+3] == 0x00) {
                System.out.printf("Found at offset %d (global %d)%n", i, 661504 + i);
            }
        }
        f.close();
    }
}
