package com.iso;
import java.io.*;

public class FindRealNames {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_fresh.iso", "r");
        
        for (int sec = 320; sec < 400; sec++) {
            f.seek(sec * 2048L);
            byte[] data = new byte[8192];
            f.readFully(data);
            
            for (int offset = 0; offset < data.length - 100; offset += 32) {
                int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
                if (tag != 257) continue;
                
                long icbLoc = ((data[offset+43]&0xFF)<<24)|((data[offset+42]&0xFF)<<16)|
                              ((data[offset+41]&0xFF)<<8)|(data[offset+40]&0xFF);
                
                if (icbLoc < 100000 || icbLoc > 50000000) continue;
                
                int nameOff = offset + 48;
                if (nameOff + 20 > data.length) continue;
                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 20 && nameOff + i*2 + 1 < data.length; i++) {
                    byte b0 = data[nameOff + i*2];
                    byte b1 = data[nameOff + i*2 + 1];
                    if (b0 >= 0x20 && b0 <= 0x7E && b1 == 0) {
                        sb.append((char)b0);
                    }
                }
                
                if (sb.length() > 2) {
                    System.out.println(sb.toString() + " -> sector " + icbLoc);
                }
            }
        }
        f.close();
    }
}
