package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class ParseMetaEntry {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        byte[] data = reader.readSectors(320, 1);
        
        int tag = ((data[1]&0xFF)<<8)|(data[0]&0xFF);
        System.out.println("Tag: " + tag);
        
        long icbLocation = ((data[19]&0xFF)<<24)|((data[18]&0xFF)<<16)|((data[17]&0xFF)<<8)|(data[16]&0xFF);
        long icbLength = ((data[23]&0xFF)<<24)|((data[22]&0xFF)<<16)|((data[21]&0xFF)<<8)|(data[20]&0xFF);
        System.out.println("ICB: loc=" + icbLocation + ", len=" + icbLength);
        
        long allocLoc = ((data[43]&0xFF)<<24)|((data[42]&0xFF)<<16)|((data[41]&0xFF)<<8)|(data[40]&0xFF);
        long allocLen = ((data[47]&0xFF)<<24)|((data[46]&0xFF)<<16)|((data[45]&0xFF)<<8)|(data[44]&0xFF);
        System.out.println("AllocDesc: loc=" + allocLoc + ", len=" + allocLen);
    }
}
