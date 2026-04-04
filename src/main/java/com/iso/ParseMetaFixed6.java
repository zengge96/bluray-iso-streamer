package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ParseMetaFixed6 {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);
        byte[] data = new byte[8192];
        f.readFully(data);
        
        int offset = 0;
        int count = 0;
        while (offset < data.length - 100 && count < 50) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset++;
                continue;
            }
            
            int nameOffset = offset + 48;
            int end = nameOffset;
            while (end < data.length - 1 && (data[end] != 0 || data[end+1] != 0)) {
                end += 2;
            }
            
            int nameLen = end - nameOffset;
            if (nameLen > 0 && nameLen < 200 && nameLen % 2 == 0) {
                try {
                    String name = new String(data, nameOffset, nameLen, StandardCharsets.UTF_16BE);
                    if (name.length() > 0) {
                        System.out.println(name);
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
