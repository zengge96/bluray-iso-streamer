import java.io.*;
import java.net.*;

public class CheckNSR {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // Check NSR03 sector
        System.out.println("=== Sector 17 (NSR03) ===");
        byte[] nsr = readSector(17);
        if (nsr != null) {
            System.out.println("First 32 bytes:");
            for (int i = 0; i < 32; i++) {
                System.out.printf("%02X ", nsr[i]);
                if ((i+1) % 16 == 0) System.out.println();
            }
        }
        
        // Now let's find where the actual data starts
        // Blu-ray often has partition at a different location
        // Try searching for "BDMV" string
        System.out.println("\n=== Searching for 'BDMV' string ===");
        for (long start = 0; start < 1000000; start += 100) {
            byte[] data = readRange(start * 2048, 2048 * 10);
            if (data == null) continue;
            
            String str = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
            int idx = str.indexOf("BDMV");
            if (idx >= 0) {
                long foundSector = start + (idx / 2048);
                System.out.println("Found BDMV at approximate sector " + foundSector + " (offset " + idx + " in buffer)");
                
                // Show context
                int contextStart = Math.max(0, idx - 20);
                System.out.println("Context: " + new String(data, contextStart, 40, java.nio.charset.StandardCharsets.ISO_8859_1).replaceAll("[^\\p{Print}]", "."));
                break;
            }
        }
        
        // Also check AVDP at sector 257 (BD-ROM alternate)
        System.out.println("\n=== Sector 257 (BD-ROM AVDP) ===");
        byte[] avdp2 = readSector(257);
        if (avdp2 != null && avdp2.length >= 32) {
            int tag = avdp2[0] | (avdp2[1] << 8);
            long main = readUInt32LE(avdp2, 16);
            System.out.println("Tag: " + tag + ", Main VDS: " + main);
        }
    }
    
    static byte[] readSector(long sector) {
        return readRange(sector * 2048, 2048);
    }
    
    static byte[] readRange(long offset, int length) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
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
    
    static long readUInt32LE(byte[] data, int offset) {
        if (offset + 4 > data.length) return 0;
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
}
