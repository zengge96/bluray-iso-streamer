import java.io.*;
import java.net.*;

public class UdfRef {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Step 1: Find AVDP ===");
        // Sector 256 is the primary AVDP location
        byte[] avdp = readSector(256);
        if (avdp == null || avdp.length < 2048) {
            System.out.println("Failed to read AVDP");
            return;
        }
        
        // Parse tag (first 2 bytes should be 2 for AVDP)
        int tagId = readUInt16LE(avdp, 0);
        System.out.println("Tag ID at sector 256: " + tagId);
        
        // Validate checksum (like pycdlib does)
        int checksum = computeChecksum(avdp);
        System.out.println("Checksum: " + checksum + " (should match byte 4)");
        
        // Main VDS location at offset 16 (ExtentAD: 4 bytes location + 4 bytes length)
        long mainVdsLoc = readUInt32LE(avdp, 16);
        long mainVdsLen = readUInt32LE(avdp, 20);
        System.out.println("Main VDS: location=" + mainVdsLoc + ", length=" + mainVdsLen);
        
        long reserveVdsLoc = readUInt32LE(avdp, 24);
        long reserveVdsLen = readUInt32LE(avdp, 28);
        System.out.println("Reserve VDS: location=" + reserveVdsLoc + ", length=" + reserveVdsLen);
        
        System.out.println("\n=== Step 2: Scan VDS at sector " + mainVdsLoc + " ===");
        // Scan VDS for descriptors
        for (long sector = mainVdsLoc; sector < mainVdsLoc + 16; sector++) {
            byte[] vd = readSector(sector);
            if (vd == null || vd.length < 4) continue;
            
            int tid = readUInt16LE(vd, 0);
            String sig = new String(vd, 4, 5);
            System.out.println("Sector " + sector + ": tag=" + tid + " sig='" + sig + "'");
            
            // Tag 5 = Partition Descriptor
            if (tid == 5) {
                long partLoc = readUInt32LE(vd, 188);
                System.out.println("  -> Partition starts at: " + partLoc);
                
                // Check for root directory ICB
                long rootIcb = readUInt32LE(vd, 216);
                System.out.println("  -> Root directory ICB: " + rootIcb);
            }
            
            // Tag 6 = Logical Volume Descriptor  
            if (tid == 6) {
                // Parse LV to find root directory
                // At offset 440: logical volume contents use
                long lvv = readUInt32LE(vd, 440);
                System.out.println("  -> LV contents use: " + lvv);
            }
        }
    }
    
    static byte[] readSector(long sector) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Range", "bytes=" + (sector * 2048) + "-" + (sector * 2048 + 2047));
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 206) return null;
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
                if (baos.size() >= 2048) break;
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
    
    // Compute checksum as pycdlib does: sum all bytes, subtract byte 4
    static int computeChecksum(byte[] data) {
        int sum = 0;
        for (int i = 0; i < 16; i++) {
            sum += data[i];
        }
        return (sum - data[4]) % 256;
    }
}
