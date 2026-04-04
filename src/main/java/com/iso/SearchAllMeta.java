package com.iso;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class SearchAllMeta {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        
        // Search sectors 320-350 in metadata partition
        for (int sec = 320; sec < 360; sec++) {
            long base = sec * 2048L;
            f.seek(base);
            byte[] data = new byte[2048];
            f.readFully(data);
            
            for (int offset = 0; offset < data.length - 64; offset += 32) {
                int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
                if (tag == 257) {
                    int nameOffset = offset + 48;
                    if (nameOffset + 2 < data.length) {
                        // Check if there's actual content
                        if (data[nameOffset] != 0 || data[nameOffset+1] != 0) {
                            int end = nameOffset;
                            while (end < data.length - 1 && (data[end] != 0 || data[end+1] != 0)) {
                                end += 2;
                            }
                            int nameLen = end - nameOffset;
                            if (nameLen > 0 && nameLen < 50 && nameLen % 2 == 0) {
                                try {
                                    String name = new String(data, nameOffset, nameLen, StandardCharsets.UTF_16BE);
                                    if (name.length() > 0) {
                                        long icbLoc = ((data[offset+43]&0xFF)<<24)|((data[offset+42]&0xFF)<<16)|
                                                      ((data[offset+41]&0xFF)<<8)|(data[offset+40]&0xFF);
                                        System.out.printf("Sec %d off %d: [%s] -> sector %d%n", sec, offset, name, icbLoc);
                                    }
                                } catch (Exception e) {}
                            }
                        }
                    }
                }
            }
        }
        f.close();
    }
}
