package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ParseMetaFixed5 {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);
        byte[] data = new byte[8192];
        f.readFully(data);
        
        System.out.println("=== Parsing FileId entries properly ===");
        
        int offset = 0;
        int count = 0;
        while (offset < data.length - 100 && count < 100) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset++;
                continue;
            }
            
            // Found FileId entry - now parse
            // Name at offset + 48 (based on earlier test)
            int nameOffset = offset + 48;
            
            // Find null terminator
            int end = nameOffset;
            while (end < data.length - 1 && (data[end] != 0 || data[end+1] != 0)) {
                end += 2;
            }
            
            int nameLen = end - nameOffset;
            if (nameLen > 0 && nameLen < 200 && nameLen % 2 == 0) {
                try {
                    String name = new String(data, nameOffset, nameLen, StandardCharsets.UTF_16BE);
                    if (name.length() > 0 && name.matches(".*[A-Za-z].*")) {
                        // ICB at offset + 40
                        long icbLoc = ((data[offset+43]&0xFF)<<24)|((data[offset+42]&0xFF)<<16)|
                                      ((data[offset+41]&0xFF)<<8)|(data[offset+40]&0xFF);
                        System.out.println(name + " -> sector " + icbLoc);
                        count++;
                    }
                } catch (Exception e) {}
            }
            
            // Move to next potential entry - add 32 or find next tag
            offset += 32;
        }
        System.out.println("Total: " + count);
        f.close();
    }
}
