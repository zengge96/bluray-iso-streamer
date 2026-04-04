package com.iso;
import java.io.*;

public class DumpNameBytes {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_fresh.iso", "r");
        
        // Sector 323 contains BDMV and CERTIFICATE entries
        f.seek(323 * 2048L);
        byte[] data = new byte[2048];
        f.readFully(data);
        
        // Look at first few FileId entries
        for (int offset = 0; offset < 500; offset += 40) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) continue;
            
            // ICB at offset +40
            long icb = ((data[offset+43]&0xFF)<<24)|((data[offset+42]&0xFF)<<16)|
                       ((data[offset+41]&0xFF)<<8)|(data[offset+40]&0xFF);
            
            // Name length at offset +46? 
            int nameLen = data[offset + 46] & 0xFF;
            
            // Name at offset +48
            System.out.printf("\nEntry at %d: ICB=%d, nameLen=%d\n", offset, icb, nameLen);
            System.out.print("Name bytes: ");
            for (int i = 48; i < 48 + 32 && i < data.length; i++) {
                System.out.printf("%02X ", data[offset + i] & 0xFF);
            }
            System.out.println();
        }
        f.close();
    }
}
