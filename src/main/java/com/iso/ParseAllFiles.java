package com.iso;
import java.io.*;
import java.util.*;

public class ParseAllFiles {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_fresh.iso", "r");
        
        Map<String, Long> files = new LinkedHashMap<>();
        
        // Scan metadata partition
        for (int sec = 320; sec < 450; sec++) {
            f.seek(sec * 2048L);
            byte[] data = new byte[2048];
            int read = f.read(data);
            if (read < 38) continue;
            
            for (int offset = 0; offset < data.length - 38; offset += 4) {
                int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
                if (tag != 257) continue; // FileId
                
                int idLen = data[offset + 19] & 0xFF;
                int implLen = ((data[offset+37]&0xFF)<<8)|(data[offset+36]&0xFF);
                
                // ICB at offset +20, location at +8 from there
                long icbLoc = ((data[offset+28]&0xFF)<<24)|((data[offset+27]&0xFF)<<16)|
                              ((data[offset+26]&0xFF)<<8)|(data[offset+25]&0xFF);
                
                // Name starts at 38 + implLen
                int nameStart = offset + 38 + implLen;
                if (nameStart + idLen > data.length) continue;
                
                // Parse dstring - if first byte is 0x10, it's UTF-16
                String name = "";
                if (idLen >= 2 && data[nameStart] == 0x10) {
                    // UTF-16BE, skip first byte
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i + 1 < idLen && nameStart + i + 1 < data.length; i += 2) {
                        char c = (char)((data[nameStart + i] << 8) | (data[nameStart + i + 1] & 0xFF));
                        if (c != 0) sb.append(c);
                    }
                    name = sb.toString();
                } else if (idLen > 0) {
                    // ASCII
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < idLen && nameStart + i < data.length; i++) {
                        char c = (char)(data[nameStart + i] & 0xFF);
                        if (c >= 0x20 && c < 0x7F) sb.append(c);
                    }
                    name = sb.toString();
                }
                
                if (name.length() > 0 && icbLoc > 100000 && !files.containsKey(name)) {
                    files.put(name, icbLoc);
                }
            }
        }
        
        System.out.println("Found " + files.size() + " files");
        
        // List m2ts files
        for (String name : files.keySet()) {
            if (name.endsWith(".m2ts")) {
                System.out.println(name + " -> " + files.get(name));
            }
        }
        
        // Also show directories
        System.out.println("\n=== Directories ===");
        for (String name : files.keySet()) {
            if (!name.contains(".")) {
                System.out.println(name + " -> " + files.get(name));
            }
        }
        
        f.close();
    }
}
