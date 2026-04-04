package com.iso;
import java.io.*;

public class CheckEntry14 {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        
        // Entry 14: offset = 13 * 32 = 416 (starting from 0)
        f.seek(661504 + 416 + 48);
        byte[] name = new byte[20];
        f.readFully(name);
        
        System.out.println("Entry 14 name area:");
        for (int i = 0; i < 20; i++) {
            System.out.printf("%02X ", name[i] & 0xFF);
        }
        
        System.out.println("\n\nAs UTF-16BE:");
        System.out.println(new String(name, java.nio.charset.StandardCharsets.UTF_16BE));
        
        System.out.println("\nAs ASCII (even bytes):");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length; i += 2) {
            if (name[i] != 0) sb.append((char)name[i]);
        }
        System.out.println(sb.toString());
        
        f.close();
    }
}
