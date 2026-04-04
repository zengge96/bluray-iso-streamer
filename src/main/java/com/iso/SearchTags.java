package com.iso;

import com.iso.udf.*;
import java.nio.file.*;

public class SearchTags {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        long partStart = parser.getPartitionStartLsn() * 2048;
        System.out.println("Searching in partition starting at byte " + partStart);
        
        // Read first 64 sectors of partition
        byte[] data = parser.readRange(partStart, 64 * 2048);
        
        System.out.println("Read " + data.length + " bytes");
        
        for (int sec = 0; sec < 64; sec++) {
            int off = sec * 2048;
            if (off + 4 > data.length) break;
            
            int tag = ((data[off+1]&0xFF)<<8)|(data[off]&0xFF);
            int ver = ((data[off+3]&0xFF)<<8)|(data[off+2]&0xFF);
            
            if (tag >= 256 && tag <= 266) {
                String name = switch(tag) {
                    case 256 -> "FileSet";
                    case 257 -> "FileId";
                    case 261 -> "File";
                    case 266 -> "ExtendedFile";
                    default -> "Unknown";
                };
                System.out.printf("Sector %d: tag=%d (%s) version=%d%n", sec, tag, name, ver);
            }
        }
    }
}
