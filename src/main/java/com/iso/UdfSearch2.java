package com.iso;

import java.io.*;
import java.net.*;

public class UdfSearch2 {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // 在更多位置搜索 BDMV
        long[] searchSectors = {
            288,      // partition start
            512, 
            1024,
            2048,
            4096,
            8192,
            16000,
            32000,
            64000,
            128000,
            256000,
            512000
        };
        
        for (long sector : searchSectors) {
            byte[] data = readRange(sector * 2048, 2048 * 16);
            if (data == null || data.length < 2048) continue;
            
            // 搜索常见字符串
            String str = new String(data, "ISO-8859-1");
            
            int idx1 = str.indexOf("BDMV");
            int idx2 = str.indexOf("STREAM");
            int idx3 = str.indexOf("m2ts");
            
            if (idx1 >= 0 || idx2 >= 0 || idx3 >= 0) {
                System.out.println("Found at sector " + sector + ":");
                if (idx1 >= 0) System.out.println("  BDMV at offset " + idx1);
                if (idx2 >= 0) System.out.println("  STREAM at offset " + idx2);
                if (idx3 >= 0) System.out.println("  m2ts at offset " + idx3);
            }
        }
        
        // 尝试搜索物理地址范围外的位置 (比如 589824，这是7z显示的)
        System.out.println("\n=== Checking sector 589824 (from 7z) ===");
        byte[] data589824 = readRange(589824L * 2048, 2048 * 16);
        if (data589824 != null) {
            String s = new String(data589824, "ISO-8859-1");
            if (s.contains("BDMV") || s.contains("STREAM") || s.contains("m2ts")) {
                System.out.println("Found data at 589824!");
            } else {
                // 检查是否是加密数据
                int nonPrintable = 0;
                for (int i = 0; i < Math.min(1000, data589824.length); i++) {
                    if (data589824[i] < 32 && data589824[i] != 0) nonPrintable++;
                }
                System.out.println("Non-printable bytes in first 1000: " + nonPrintable + "/1000");
            }
        }
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
            return null;
        }
    }
}
