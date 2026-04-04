package com.iso;

import java.io.*;
import java.net.*;

public class UdfMeta {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // Metadata partition at sector 655360
        long metaPartition = 655360;
        
        System.out.println("=== Checking Metadata Partition at sector " + metaPartition + " ===");
        
        // 读取前几个sector
        for (int i = 0; i < 16; i++) {
            byte[] sec = readSector(metaPartition + i);
            if (sec == null || sec.length < 64) continue;
            
            int tag = (sec[0] & 0xFF) | ((sec[1] & 0xFF) << 8);
            String sig = new String(sec, 4, 5);
            
            System.out.println("Sector " + (metaPartition + i) + ": tag=" + tag + " sig='" + sig + "'");
            
            // 如果是 File Entry (261)，尝试列出
            if (tag == 261) {
                int fileType = sec[18] & 0xFF;
                long infoLen = readUInt64LE(sec, 72);
                System.out.println("  -> File type: " + fileType + " (4=dir), size: " + infoLen);
                
                // 列出目录内容
                if (fileType == 4) {
                    listDirectory(sec);
                }
            }
            
            // File Set Descriptor
            if (tag == 0 || sig.equals("FSD")) {
                System.out.println("  -> File Set Descriptor found!");
            }
        }
    }
    
    static void listDirectory(byte[] dirData) {
        int pos = 0;
        int count = 0;
        
        while (pos < dirData.length - 36 && count < 30) {
            int entryLen = (dirData[pos] & 0xFF) | ((dirData[pos+1] & 0xFF) << 8);
            if (entryLen == 0 || entryLen < 36) break;
            if (pos + entryLen > dirData.length) break;
            
            long icbLoc = readUInt32LE(dirData, pos + 20);
            int fileType = dirData[pos + 18] & 0xFF;
            int nameLen = dirData[pos + 32] & 0xFF;
            
            if (nameLen > 0 && nameLen < 128 && pos + 33 + nameLen <= dirData.length) {
                String name = "";
                try {
                    if (nameLen >= 2 && dirData[pos+33] == 0 && dirData[pos+34] != 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < nameLen - 1; j += 2) {
                            char c = (char) ((dirData[pos+33+j] << 8) | (dirData[pos+33+j+1] & 0xFF));
                            if (c == 0) break;
                            sb.append(c);
                        }
                        name = sb.toString();
                    } else {
                        name = new String(dirData, pos + 33, nameLen, "ISO-8859-1").trim();
                    }
                } catch (Exception e) {
                    name = "?";
                }
                
                if (!name.isEmpty() && !name.equals(".") && !name.equals("..")) {
                    String type = fileType == 4 ? "[DIR]" : "[FILE]";
                    System.out.println("  " + type + " " + name + " (ICB=" + icbLoc + ")");
                    count++;
                }
            }
            
            pos += entryLen;
        }
    }
    
    static byte[] readSector(long sector) {
        return readRange(sector * 2048, 2048);
    }
    
    static byte[] readRange(long offset, int length) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() != 206) return null;
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
                if (baos.size() >= length) break;
            }
            is.close();
            conn.disconnect();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    static long readUInt32LE(byte[] data, int offset) {
        if (offset + 4 > data.length) return 0;
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
    
    static long readUInt64LE(byte[] data, int offset) {
        return readUInt32LE(data, offset) | (readUInt32LE(data, offset + 4) << 32);
    }
}
