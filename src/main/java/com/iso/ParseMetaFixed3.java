package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ParseMetaFixed3 {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);
        byte[] data = new byte[8192];
        f.readFully(data);
        
        System.out.println("=== Parsing FileId entries (offset 48) ===");
        
        // Entry starts at offset 32, name at 48 from entry start
        // Entry starts at offset 64, name at 80 from sector start = 80-64=16... no
        
        // Try: find tag 257, then name at offset + 48
        int offset = 0;
        int count = 0;
        while (offset < data.length - 100 && count < 50) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset += 4;
                continue;
            }
            
            // Name at offset + 48, length at offset + 33
            int nameLen = data[offset + 33] & 0xFF;
            int nameOffset = offset + 48;
            
            if (nameLen > 1 && nameOffset + nameLen <= data.length) {
                try {
                    String name = new String(data, nameOffset, nameLen - 1, StandardCharsets.UTF_16BE);
                    name = name.replace("\u0000", "").trim();
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
