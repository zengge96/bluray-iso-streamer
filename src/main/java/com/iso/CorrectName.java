package com.iso;
import java.io.*;

public class CorrectName {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_fresh.iso", "r");
        
        f.seek(323 * 2048L);
        byte[] data = new byte[2048];
        f.readFully(data);
        
        // FileId entry: tag at offset, then:
        // offset 16: file version (2 bytes)
        // offset 18: file char (1 byte)
        // offset 19: id length (1 byte)
        // offset 20: ICB (32 bytes) - location at offset 20+8 = 28
        // offset 36: impl use len (2 bytes)  
        // offset 38: file id string starts
        
        // For entry at offset 40 (second entry):
        int entryStart = 40;
        
        // File ID length at offset 19
        int idLen = data[entryStart + 19] & 0xFF;
        // Impl use len at offset 36
        int implLen = ((data[entryStart + 37]&0xFF)<<8)|(data[entryStart + 36]&0xFF);
        
        // ICB location at offset 20+8 = 28 from entry start
        long icbLoc = ((data[entryStart + 28]&0xFF)<<24)|((data[entryStart + 27]&0xFF)<<16)|
                      ((data[entryStart + 26]&0xFF)<<8)|(data[entryStart + 25]&0xFF);
        
        // File ID starts at offset 38
        int nameStart = entryStart + 38 + implLen;
        
        System.out.println("Entry at offset " + entryStart);
        System.out.println("ID length: " + idLen);
        System.out.println("Impl use len: " + implLen);
        System.out.println("ICB location: " + icbLoc);
        System.out.println("Name starts at: " + nameStart);
        
        // Print name bytes (dstring - may be compressed)
        System.out.print("Name bytes: ");
        for (int i = nameStart; i < nameStart + idLen && i < data.length; i++) {
            System.out.printf("%02X ", data[i] & 0xFF);
        }
        System.out.println();
        
        // Check if compressed (high bit set = escape sequence)
        // First byte 0x01 means OSTA CS0 compression
        if (data[nameStart] == 0x01) {
            System.out.println("Using OSTA CS0 compression!");
            // Next byte is character set (0x00 = reserved, 0x01 = OSTA)
            // Then compressed data
        }
        
        f.close();
    }
}
