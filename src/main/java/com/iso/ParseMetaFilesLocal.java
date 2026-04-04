package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ParseMetaFilesLocal {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        
        // Sector 323 = offset 661504
        f.seek(661504);
        byte[] data = new byte[8192];
        f.readFully(data);
        
        System.out.println("=== Parsing FileId entries (local file) ===");
        
        int offset = 0;
        int count = 0;
        while (offset < data.length - 64 && count < 30) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset += 4;
                continue;
            }
            
            int implUseLen = ((data[offset+37]&0xFF)<<8)|(data[offset+36]&0xFF);
            int nameLen = data[offset+25] & 0xFF;
            
            int nameOffset = offset + 38 + implUseLen;
            
            if (nameLen > 0 && nameOffset + nameLen <= data.length) {
                try {
                    String name = new String(data, nameOffset, Math.min(nameLen, 30), StandardCharsets.UTF_16BE);
                    name = name.replace("\u0000", "");
                    
                    if (name.length() > 0 && name.matches(".*[A-Za-z].*")) {
                        System.out.println("Entry " + count + ": [" + name + "] len=" + nameLen + " impl=" + implUseLen);
                        count++;
                    }
                } catch (Exception e) {}
            }
            
            offset += 32;
        }
        System.out.println("Total: " + count);
        f.close();
    }
}
