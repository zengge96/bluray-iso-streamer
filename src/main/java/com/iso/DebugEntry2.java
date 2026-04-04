package com.iso;
import java.io.*;

public class DebugEntry2 {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504 + 32);  // Entry 2
        byte[] ent = new byte[60];
        f.readFully(ent);
        
        System.out.println("Entry 2 at sector offset 32:");
        System.out.println("Byte 33 (name len?): " + (ent[33] & 0xFF));
        System.out.println("Byte 34-35 (impl use len?): " + ((ent[35]&0xFF)<<8|(ent[34]&0xFF)));
        
        System.out.println("\nBytes 40-55 (ICB):");
        for (int i = 40; i < 56; i++) {
            System.out.printf("%02X ", ent[i] & 0xFF);
        }
        
        System.out.println("\nBytes 48-60 (name area):");
        for (int i = 48; i < 60; i++) {
            System.out.printf("%02X ", ent[i] & 0xFF);
        }
        
        // Try reading as UTF-16BE from 48
        System.out.println("\nText at 48: ");
        byte[] nameArea = new byte[20];
        System.arraycopy(ent, 48, nameArea, 0, 20);
        String s = new String(nameArea, java.nio.charset.StandardCharsets.UTF_16BE);
        System.out.println(s);
        
        f.close();
    }
}
