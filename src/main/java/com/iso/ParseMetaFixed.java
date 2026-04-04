package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ParseMetaFixed {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);
        byte[] data = new byte[8192];
        f.readFully(data);
        
        System.out.println("=== Parsing FileId entries (40-byte stride) ===");
        
        int offset = 0;
        int count = 0;
        while (offset < data.length - 64 && count < 30) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset += 4;
                continue;
            }
            
            // FileId structure:
            // Tag(16) + ICB(16) + FileChar(1) + NameLen(1) + ImplUseLen(2) + ICB(16) + ImplUse + Name
            // Wait, need to check exact layout
            
            // Name length at offset 17 (after Tag 16 + FileChar 1)
            int nameLen = data[offset + 17] & 0xFF;
            int implUseLen = ((data[offset+19]&0xFF)<<8)|(data[offset+18]&0xFF);
            
            // Name starts after: tag(16) + ICB(16) + FileChar(1) + NameLen(1) + ImplUseLen(2) + ICB(16) = 52
            int nameOffset = offset + 52 + implUseLen;
            
            System.out.printf("Entry %d at offset %d: len=%d impl=%d nameOff=%d%n", 
                count, offset, nameLen, implUseLen, nameOffset);
            
            if (nameLen > 1 && nameOffset + nameLen <= data.length) {
                try {
                    String name = new String(data, nameOffset, nameLen - 1, StandardCharsets.UTF_16BE);
                    name = name.replace("\u0000", "");
                    if (name.length() > 0) {
                        System.out.println("  -> " + name);
                        count++;
                    }
                } catch (Exception e) {
                    System.out.println("  Error: " + e.getMessage());
                }
            }
            
            offset += 40;
        }
        System.out.println("Total: " + count);
        f.close();
    }
}
