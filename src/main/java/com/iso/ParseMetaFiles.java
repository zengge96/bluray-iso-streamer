package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class ParseMetaFiles {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read sector 323 (metadata with file entries)
        byte[] data = reader.readSectors(323, 8);
        
        System.out.println("=== Parsing FileId entries ===");
        
        int offset = 0;
        int count = 0;
        while (offset < data.length - 64 && count < 30) {
            int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
            if (tag != 257) {
                offset += 4;
                continue;
            }
            
            // FileId found - parse it
            // Fixed header is 36 bytes (16 tag + 16 ICB + 1 + 1 + 2)
            // Then implUseLen (2 bytes), then implUse, then name
            int implUseLen = ((data[offset+37]&0xFF)<<8)|(data[offset+36]&0xFF);
            int nameLen = data[offset+25] & 0xFF;
            
            int nameOffset = offset + 38 + implUseLen; // 36 + 2 = 38
            
            if (nameLen > 0 && nameOffset + nameLen <= data.length) {
                try {
                    String name = new String(data, nameOffset, nameLen - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                    name = name.replace("\u0000", "");
                    
                    // Get ICB location
                    int icbOffset = offset + 28;
                    long icbLoc = ((data[icbOffset+3]&0xFF)<<24)|((data[icbOffset+2]&0xFF)<<16)|
                                  ((data[icbOffset+1]&0xFF)<<8)|(data[icbOffset]&0xFF);
                    
                    if (name.length() > 0) {
                        System.out.println(name + " -> sector " + icbLoc);
                        count++;
                    }
                } catch (Exception e) {
                    // skip
                }
            }
            
            offset += 32; // move to next entry
        }
        System.out.println("Total: " + count);
    }
}
