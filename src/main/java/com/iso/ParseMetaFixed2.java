package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ParseMetaFixed2 {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);
        byte[] data = new byte[8192];
        f.readFully(data);
        
        System.out.println("=== Parsing FileId entries (corrected) ===");
        
        int offset = 0;
        int count = 0;
        while (offset < data.length - 100 && count < 50) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset += 4;
                continue;
            }
            
            // Correct offsets:
            // Tag(16) + ICB(16) + FileChar(1) = 33
            int nameLen = data[offset + 33] & 0xFF;
            int implUseLen = ((data[offset+35]&0xFF)<<8)|(data[offset+34]&0xFF);
            
            // Name starts at: 16 + 16 + 1 + 1 + 2 + 16 + implUseLen = 52 + implUseLen
            int nameOffset = offset + 52 + implUseLen;
            
            if (nameLen > 1 && nameOffset + nameLen <= data.length) {
                try {
                    String name = new String(data, nameOffset, Math.min(nameLen, 40), StandardCharsets.UTF_16BE);
                    name = name.replace("\u0000", "").trim();
                    if (name.length() > 0) {
                        System.out.println("[" + name + "] len=" + nameLen + " impl=" + implUseLen);
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
