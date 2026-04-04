package com.iso;

import java.io.*;
import java.net.*;

public class UdfSearch {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // 读取更大的数据块来分析
        long partitionStart = 288;
        
        // 读取 partition 开始后的 1MB 数据
        System.out.println("Reading partition area (sector " + partitionStart + ")...");
        byte[] data = readRange(partitionStart * 2048, 1024 * 1024);
        
        if (data == null || data.length < 2048) {
            System.out.println("Failed to read data");
            return;
        }
        
        System.out.println("Read " + data.length + " bytes");
        
        // 搜索常见的Blu-ray目录名
        String[] searchNames = {"BDMV", "STREAM", "CERTIFICATE", "META", "BDJO", "JAR"};
        
        for (String name : searchNames) {
            byte[] searchBytes = name.getBytes();
            for (int i = 0; i < data.length - searchBytes.length; i++) {
                boolean found = true;
                for (int j = 0; j < searchBytes.length; j++) {
                    if (data[i + j] != searchBytes[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    long sector = partitionStart + (i / 2048);
                    int offset = i % 2048;
                    System.out.println("Found '" + name + "' at byte " + i + " (sector " + sector + ", offset " + offset + ")");
                    
                    // 显示周围数据
                    int ctxStart = Math.max(0, i - 16);
                    int ctxLen = Math.min(data.length - ctxStart, 48);
                    System.out.print("  Context: ");
                    for (int k = ctxStart; k < ctxStart + ctxLen; k++) {
                        char c = (char) data[k];
                        System.out.print(c >= 32 && c < 127 ? c : '.');
                    }
                    System.out.println();
                    
                    // 尝试解析前面的 FID 结构
                    // FID: 2 bytes (length) + ... + filename
                    // 查找前一个entry的起始位置
                    for (int back = i - 100; back < i; back++) {
                        if (back < 0) continue;
                        // 读取可能的entry长度
                        int entryLen = (data[back] & 0xFF) | ((data[back + 1] & 0xFF) << 8);
                        if (entryLen > 0 && entryLen < 512 && back + entryLen >= i) {
                            System.out.println("  Possible FID at " + back + " (len=" + entryLen + ")");
                            break;
                        }
                    }
                    
                    break;
                }
            }
        }
    }
    
    static byte[] readRange(long offset, int length) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            if (conn.getResponseCode() != 206) return null;
            
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
                if (baos.size() >= length) break;
            }
            is.close();
            conn.disconnect();
            return baos.toByteArray();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return null;
        }
    }
}
