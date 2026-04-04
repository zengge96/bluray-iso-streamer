package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class ReadMetaFiles {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Read from sector 1186
        byte[] data = reader.readSectors(1186, 32);
        
        System.out.println("=== Metadata at sector 1186 ===");
        
        // Look for FileSet (tag 256)
        for (int i = 0; i < data.length - 40; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag == 256) {
                System.out.println("Found FileSet at offset " + i);
                if (i + 416 <= data.length) {
                    long rootPos = ((data[i+403]&0xFF)<<24)|((data[i+402]&0xFF)<<16)|((data[i+401]&0xFF)<<8)|(data[i+400]&0xFF);
                    long rootLen = ((data[i+407]&0xFF)<<24)|((data[i+406]&0xFF)<<16)|((data[i+405]&0xFF)<<8)|(data[i+404]&0xFF);
                    System.out.println("  RootDir: sector " + rootPos + ", len " + rootLen);
                }
            }
        }
        
        // Search for FileId (tag 257)
        System.out.println("\n=== FileId entries ===");
        int count = 0;
        for (int i = 0; i < data.length - 50 && count < 30; i += 4) {
            int tag = ((data[i+1]&0xFF)<<8)|(data[i]&0xFF);
            if (tag == 257) {
                int len = data[i + 19] & 0xFF;
                if (len > 1 && len < 100 && i + 38 + len <= data.length) {
                    boolean isDir = (data[i + 18] & 1) != 0;
                    try {
                        String name = new String(data, i + 38, len - 1, java.nio.charset.StandardCharsets.UTF_16BE);
                        if (!name.trim().isEmpty() && !name.contains("\u0000")) {
                            System.out.println((isDir ? "[DIR] " : "[FILE] ") + name);
                            count++;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        System.out.println("Total: " + count);
    }
}
