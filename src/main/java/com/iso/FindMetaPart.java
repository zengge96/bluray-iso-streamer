package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class FindMetaPart {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read sector 32 and 33 for partition descriptors
        byte[] pd1 = reader.readSectors(32, 1);
        byte[] pd2 = reader.readSectors(33, 1);
        
        for (int i = 0; i < 2; i++) {
            byte[] pd = (i == 0) ? pd1 : pd2;
            int tag = ((pd[1]&0xFF)<<8)|(pd[0]&0xFF);
            System.out.println("Sector " + (32+i) + ": tag=" + tag);
            
            if (tag == 5) {
                String contents = new String(pd, 56, 32, "ISO-8859-1").trim();
                System.out.println("  Contents: " + contents);
                
                long partStart = ((pd[191]&0xFF)<<24)|((pd[190]&0xFF)<<16)|((pd[189]&0xFF)<<8)|(pd[188]&0xFF);
                long partLen = ((pd[195]&0xFF)<<24)|((pd[194]&0xFF)<<16)|((pd[193]&0xFF)<<8)|(pd[192]&0xFF);
                System.out.println("  Partition: sector " + partStart + ", len " + partLen);
            }
        }
    }
}
