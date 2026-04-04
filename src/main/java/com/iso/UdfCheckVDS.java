package com.iso;

import java.io.*;
import java.net.*;

public class UdfCheckVDS {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // AVDP says VDS at 32768, but we found valid VD at 32
        // Let's check 32768 area more carefully
        System.out.println("=== Checking sector 32768 (VDS from AVDP) ===");
        
        // Read 16 sectors starting at 32768
        byte[] vds = readRange(32768L * 2048, 32 * 2048);
        if (vds != null) {
            System.out.println("Read " + vds.length + " bytes");
            
            // 搜索有效的UDF tag
            for (int i = 0; i < vds.length; i += 2048) {
                if (i + 16 > vds.length) break;
                
                int tag = (vds[i] & 0xFF) | ((vds[i+1] & 0xFF) << 8);
                int ver = (vds[i+2] & 0xFF) | ((vds[i+3] & 0xFF) << 8);
                
                // UDF tags: 1=PVD, 2=AVDP, 5=Partition, 6=LVD, 7=USD, 8=Terminating
                if ((tag == 1 || tag == 5 || tag == 6 || tag == 7 || tag == 8) && (ver == 2 || ver == 3)) {
                    String sig = new String(vds, i+4, 5);
                    System.out.println("Sector " + (32768 + i/2048) + ": tag=" + tag + " ver=" + ver + " sig='" + sig + "'");
                }
            }
        }
        
        // Also check the backup VDS location
        System.out.println("\n=== Checking backup VDS area ===");
        // Usually 256 sectors before end
        long endSector = 45459311;
        long backupVDS = endSector - 256;
        System.out.println("Checking sector " + backupVDS);
        
        byte[] backup = readSector(backupVDS);
        if (backup != null) {
            int tag = (backup[0] & 0xFF) | ((backup[1] & 0xFF) << 8);
            System.out.println("Tag at " + backupVDS + ": " + tag);
            
            if (tag == 2) {
                long mainVDS = readUInt32LE(backup, 16);
                System.out.println("Main VDS: " + mainVDS);
            }
        }
        
        // Let me re-verify sector 32's partition info
        System.out.println("\n=== Re-checking sector 32-37 (found earlier) ===");
        for (int s = 32; s < 38; s++) {
            byte[] sec = readSector(s);
            if (sec == null || sec.length < 4) continue;
            int tag = (sec[0] & 0xFF) | ((sec[1] & 0xFF) << 8);
            String sig = new String(sec, 4, 5);
            System.out.println("Sector " + s + ": tag=" + tag + " sig='" + sig + "'");
            
            if (tag == 5) {  // Partition
                long partLoc = readUInt32LE(sec, 188);
                System.out.println("  -> Partition starts at sector " + partLoc);
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
    
    static long readUInt32LE(byte[] data, int offset) {
        if (offset + 4 > data.length) return 0;
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
}
