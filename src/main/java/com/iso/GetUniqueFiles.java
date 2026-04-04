package com.iso;
import java.io.*;
import java.util.*;

public class GetUniqueFiles {
    public static void main(String[] args) throws Exception {
        RandomAccessFile f = new RandomAccessFile("/tmp/bluray_fresh.iso", "r");
        
        Map<String, Long> fileMap = new LinkedHashMap<>();
        
        for (int sec = 320; sec < 400; sec++) {
            f.seek(sec * 2048L);
            byte[] data = new byte[8192];
            f.readFully(data);
            
            for (int offset = 0; offset < data.length - 100; offset += 32) {
                int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
                if (tag != 257) continue;
                
                long icbLoc = ((data[offset+43]&0xFF)<<24)|((data[offset+42]&0xFF)<<16)|
                              ((data[offset+41]&0xFF)<<8)|(data[offset+40]&0xFF);
                
                if (icbLoc < 100000 || icbLoc > 50000000) continue;
                
                int nameOff = offset + 48;
                if (nameOff + 20 > data.length) continue;
                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 20 && nameOff + i*2 + 1 < data.length; i++) {
                    byte b0 = data[nameOff + i*2];
                    byte b1 = data[nameOff + i*2 + 1];
                    if (b0 >= 0x20 && b0 <= 0x7E && b1 == 0) {
                        sb.append((char)b0);
                    }
                }
                
                String name = sb.toString().trim();
                if (name.length() > 3 && !fileMap.containsKey(name)) {
                    fileMap.put(name, icbLoc);
                }
            }
        }
        
        System.out.println("Found " + fileMap.size() + " unique files");
        
        // Print unique m2ts files
        List<String> m2ts = new ArrayList<>();
        for (String name : fileMap.keySet()) {
            if (name.contains(".m2ts")) {
                m2ts.add(name + " -> " + fileMap.get(name));
            }
        }
        
        System.out.println("\n=== m2ts files (" + m2ts.size() + ") ===");
        for (String s : m2ts) System.out.println(s);
        
        f.close();
    }
}
