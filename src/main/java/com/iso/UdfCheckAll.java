package com.iso;

import java.io.*;
import java.net.*;

public class UdfCheckAll {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    static long ISO_SIZE = 93100670976L;
    
    public static void main(String[] args) throws Exception {
        System.out.println("ISO size: " + (ISO_SIZE / 1024 / 1024 / 1024) + " GB");
        
        // 1. Check AVDP at sector 256
        System.out.println("\n=== AVDP at sector 256 ===");
        byte[] avdp256 = readSector(256);
        if (avdp256 != null) {
            long mainVDS = readUInt32LE(avdp256, 16);
            long mainLen = readUInt32LE(avdp256, 20);
            long resVDS = readUInt32LE(avdp256, 24);
            long resLen = readUInt32LE(avdp256, 28);
            System.out.println("Main VDS: " + mainVDS + " (len=" + mainLen + ")");
            System.out.println("Reserve VDS: " + resVDS + " (len=" + resLen + ")");
        }
        
        // 2. Check AVDP at ISO end
        System.out.println("\n=== AVDP at ISO end ===");
        long lastSector = (ISO_SIZE / 2048) - 1;
        System.out.println("Last sector: " + lastSector);
        byte[] avdpEnd = readSector(lastSector);
        if (avdpEnd != null) {
            int tag = readUInt16LE(avdpEnd, 0);
            System.out.println("Tag: " + tag);
            if (tag == 2) {
                long mainVDS = readUInt32LE(avdpEnd, 16);
                System.out.println("Main VDS: " + mainVDS);
            }
        }
        
        // 3. Check sector 257 (BD-ROM alternate)
        System.out.println("\n=== AVDP at sector 257 ===");
        byte[] avdp257 = readSector(257);
        if (avdp257 != null) {
            int tag = readUInt16LE(avdp257, 0);
            System.out.println("Tag: " + tag);
        }
        
        // 4. Scan all AVDP locations
        System.out.println("\n=== Scanning for more AVDPs ===");
        long[] avdpLocs = {256, 257, lastSector};
        for (long loc : avdpLocs) {
            byte[] avdp = readSector(loc);
            if (avdp != null && readUInt16LE(avdp, 0) == 2) {
                long mvds = readUInt32LE(avdp, 16);
                System.out.println("Sector " + loc + ": Main VDS = " + mvds);
            }
        }
        
        // 5. Check if VDS has different content
        System.out.println("\n=== Main VDS content (sector 32) ===");
        byte[] vds = readSector(32);
        if (vds != null) {
            System.out.println("First 64 bytes: ");
            for (int i = 0; i < 64; i++) {
                System.out.printf("%02X ", vds[i]);
                if ((i+1) % 16 == 0) System.out.println();
            }
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
}
