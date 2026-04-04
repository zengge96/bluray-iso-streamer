package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class TestFileList {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        System.out.println("=== ISO Info ===");
        System.out.println("Sector Size: " + parser.getSectorSize());
        System.out.println("Partition Start: sector " + parser.getPartitionStartLsn());
        
        byte[] data = parser.readSectors(288, 1);
        
        if (data != null && data.length >= 2048) {
            int tagId = ((data[1]&0xFF)<<8)|(data[0]&0xFF);
            System.out.println("Tag: " + tagId + " (266=Extended File Entry)");
            
            int fileType = data[19] & 0xFF;
            System.out.println("File Type: " + fileType + " (1=dir, 2=file)");
            
            int extAttrLen = ((data[171]&0xFF)<<24)|((data[170]&0xFF)<<16)
                |((data[169]&0xFF)<<8)|(data[168]&0xFF);
            int allocDescLen = ((data[175]&0xFF)<<24)|((data[174]&0xFF)<<16)
                |((data[173]&0xFF)<<8)|(data[172]&0xFF);
            System.out.println("ExtAttrLen=" + extAttrLen + ", AllocDescLen=" + allocDescLen);
            
            int allocStart = 176 + extAttrLen;
            if (data.length >= allocStart + 16) {
                long adPos = ((data[allocStart+7]&0xFF)<<24)|((data[allocStart+6]&0xFF)<<16)
                    |((data[allocStart+5]&0xFF)<<8)|(data[allocStart+4]&0xFF);
                long adLen = ((data[allocStart+3]&0xFF)<<24)|((data[allocStart+2]&0xFF)<<16)
                    |((data[allocStart+1]&0xFF)<<8)|(data[allocStart]&0xFF);
                System.out.println("First AD: sector " + adPos + ", len " + adLen);
                
                if (adPos > 0) {
                    byte[] dirData = parser.readSectors(adPos, (int)Math.min(adLen/2048+1, 32));
                    if (dirData != null) {
                        System.out.println("Read " + dirData.length + " bytes directory data");
                        
                        // Search for FileId (tag 257)
                        for (int i = 0; i < dirData.length - 40; i+=4) {
                            int t = ((dirData[i+1]&0xFF)<<8)|(dirData[i]&0xFF);
                            if (t == 257) {
                                int idLen = dirData[i+18] & 0xFF;
                                if (idLen > 0 && i + 38 + idLen <= dirData.length) {
                                    String name = new String(dirData, i + 38, idLen - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                                    System.out.println("  Found file: " + name);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
