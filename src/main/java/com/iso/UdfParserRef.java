package com.iso;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * 基于 pycdlib 逻辑的 UDF 解析器
 * 参考: https://github.com/clalancette/pycdlib
 */
public class UdfParserRef {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    static int SECTOR_SIZE = 2048;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== UDF Parser (pycdlib reference) ===");
        
        // Step 1: Find AVDP at sector 256
        System.out.println("\n1. Finding AVDP at sector 256...");
        byte[] avdp = readSector(256);
        if (avdp == null || avdp.length < 32) {
            System.out.println("Failed to read AVDP");
            return;
        }
        
        int tagId = readUInt16LE(avdp, 0);
        System.out.println("   Tag ID: " + tagId + " (expected 2)");
        
        // Step 2: Scan sector 32-40 to find VDS
        System.out.println("\n2. Scanning for Volume Descriptors (sector 32-40)...");
        long partitionStart = -1;
        long rootIcb = -1;
        
        for (int s = 32; s < 40; s++) {
            byte[] vd = readSector(s);
            if (vd == null || vd.length < 200) continue;
            
            int tag = readUInt16LE(vd, 0);
            int ver = readUInt16LE(vd, 2);
            
            if (tag == 1) {
                System.out.println("   Sector " + s + ": Primary Volume Descriptor");
            } else if (tag == 5) {
                // Partition Descriptor
                partitionStart = readUInt32LE(vd, 188);
                System.out.println("   Sector " + s + ": Partition Descriptor, partition starts at " + partitionStart);
            } else if (tag == 6) {
                // Logical Volume Descriptor
                rootIcb = readUInt32LE(vd, 472);
                long rootLen = readUInt32LE(vd, 476);
                System.out.println("   Sector " + s + ": Logical Volume Descriptor");
                System.out.println("   Root Directory ICB: " + rootIcb + " (len=" + rootLen + ")");
            }
        }
        
        // Step 3: Find root directory
        System.out.println("\n3. Finding root directory...");
        
        // Method 1: Use rootIcb directly if in partition space
        // Method 2: Scan partition area for File Entries
        
        if (partitionStart > 0 && rootIcb > 0) {
            // Convert logical to physical
            long rootSector = rootIcb - partitionStart;
            System.out.println("   Looking for root at physical sector: " + rootSector);
            
            // Only try if within reasonable range
            if (rootSector > 288 && rootSector < 1000000) {
                byte[] rootDir = readSector(rootSector);
                if (rootDir != null && rootDir.length >= 200) {
                    int rootTag = readUInt16LE(rootDir, 0);
                    System.out.println("   Root sector tag: " + rootTag);
                    
                    if (rootTag == 261) {
                        long infoLen = readUInt64LE(rootDir, 72);
                        System.out.println("   Root directory size: " + infoLen + " bytes");
                    }
                }
            }
        }
        
        // Fallback: scan partition area
        System.out.println("\n4. Scanning partition area for directories...");
        scanForDirectories(partitionStart > 0 ? partitionStart : 288);
    }
    
    static void scanForDirectories(long startSector) {
        // 只扫描前几个sector
        for (int i = 0; i < 32; i++) {
            byte[] sec = readSector(startSector + i);
            if (sec == null || sec.length < 64) continue;
            
            int tag = readUInt16LE(sec, 0);
            if (tag == 261) {
                int fileType = sec[18] & 0xFF;
                long infoLen = readUInt64LE(sec, 72);
                
                if (fileType == 4) {  // Directory
                    System.out.println("   Found directory at sector " + (startSector + i) + ", size=" + infoLen);
                }
            }
        }
    }
    
    // ===== Utilities =====
    
    static byte[] readSector(long sector) {
        return readRange(sector * SECTOR_SIZE, SECTOR_SIZE);
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
    
    static int readUInt16LE(byte[] data, int offset) {
        if (offset + 2 > data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
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
