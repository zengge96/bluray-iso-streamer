package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ParseMetaFixed4 {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);
        byte[] data = new byte[8192];
        f.readFully(data);
        
        System.out.println("=== Parsing with null-terminated names ===");
        
        int offset = 0;
        int count = 0;
        while (offset < data.length - 100 && count < 50) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset += 4;
                continue;
            }
            
            // Name at offset + 48, read until null
            int nameOffset = offset + 48;
            int end = nameOffset;
            while (end < data.length && data[end] != 0) {
                end += 2;  // UTF-16BE
            }
            
            int nameLen = end - nameOffset;
            if (nameLen > 0 && nameLen < 100) {
                try {
                    String name = new String(data, nameOffset, nameLen, StandardCharsets.UTF_16BE);
                    if (name.length() > 0) {
                        // Get ICB location at offset + 40
                        long icbLoc = ((data[offset+43]&0xFF)<<24)|((data[offset+42]&0xFF)<<16)|
                                      ((data[offset+41]&0xFF)<<8)|(data[offset+40]&0xFF);
                        System.out.println(name + " -> sector " + icbLoc);
                        count++;
                    }
                } catch (Exception e) {}
            }
            
            offset += 40;
        }
        System.out.println("Total: " + count);
        f.close();
    }
}
