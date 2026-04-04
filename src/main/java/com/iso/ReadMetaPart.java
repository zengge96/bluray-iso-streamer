package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class ReadMetaPart {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read metadata partition at sector 320
        byte[] data = reader.readSectors(320, 32);
        
        System.out.println("=== Metadata Partition at sector 320 ===");
        
        // Search for UDF tags
        for (int i = 0; i < data.length - 40; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag >= 256 && tag <= 266) {
                int ver = ((data[3]&0xFF)<<8)|(data[2]&0xFF);
                System.out.printf("Tag %d at offset %d, ver=%d%n", tag, i, ver);
                
                if (tag == 256) {  // FileSet
                    long root = ((data[i+403]&0xFF)<<24)|((data[i+402]&0xFF)<<16)|((data[i+401]&0xFF)<<8)|(data[i+400]&0xFF);
                    System.out.println("  RootDir ICB: sector " + root);
                }
                if (tag == 257) {  // FileId
                    int len = data[i + 19] & 0xFF;
                    if (len > 1 && len < 100 && i + 38 + len <= data.length) {
                        boolean isDir = (data[i + 18] & 1) != 0;
                        try {
                            String name = new String(data, i + 38, len - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                            if (!name.trim().isEmpty()) {
                                System.out.println((isDir ? "[DIR] " : "[FILE] ") + name);
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        }
    }
}
