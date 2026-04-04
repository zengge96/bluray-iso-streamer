package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class DebugEntry2 {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] data = reader.readSectors(288, 1);  // Partition start
        
        if (data != null && data.length >= 2048) {
            // Tag ID at offset 0
            int tag = ((data[1]&0xFF)<<8)|(data[0]&0xFF);
            System.out.println("Tag: " + tag);
            
            // ICB Tag starts at offset 16
            // ICB File Type at offset 16 + 11 = 27
            int fileType = data[27] & 0xFF;
            System.out.println("File Type (at offset 27): " + fileType + " (1=dir, 2=file)");
            
            // File size at offset 56
            long size = 0;
            for (int i = 0; i < 8; i++) size = (size << 8) | (data[56 + i] & 0xFF);
            System.out.println("File size: " + size);
            
            // Extended Attr Len at offset 208 (168+40 for Extended File Entry)
            int eaLen = ((data[211]&0xFF)<<24)|((data[210]&0xFF)<<16)|((data[209]&0xFF)<<8)|(data[208]&0xFF);
            System.out.println("Ext Attr Len (at 208): " + eaLen);
            
            // Alloc Desc Len at offset 212
            int adLen = ((data[215]&0xFF)<<24)|((data[214]&0xFF)<<16)|((data[213]&0xFF)<<8)|(data[212]&0xFF);
            System.out.println("Alloc Desc Len (at 212): " + adLen);
        }
    }
}
