package com.iso;
import java.io.*;

public class DebugTags {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_100m.iso", "r");
        f.seek(661504);  // sector 323
        
        byte[] data = new byte[2048];
        f.readFully(data);
        
        System.out.println("Tags in sector 323:");
        for (int i = 0; i < 2048 - 4; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag >= 256 && tag <= 266) {
                System.out.printf("Offset %d: tag %d%n", i, tag);
            }
        }
        
        System.out.println("\nFirst 32 bytes:");
        for (int i = 0; i < 32; i++) {
            System.out.printf("%02X ", data[i] & 0xFF);
        }
        f.close();
    }
}
