import java.io.*;
import java.net.*;

public class CheckISO {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== ISO Header Sectors 0-32 ===");
        for (int s = 0; s < 32; s++) {
            byte[] sec = readSector(s);
            if (sec == null || sec.length < 32) continue;
            
            // Check for standard identifiers
            String sig4 = new String(sec, 0, 4);
            String sig5 = new String(sec, 4, 5);
            int tag = sec[0] | (sec[1] << 8);
            
            System.out.printf("Sector %3d: '%.4s' '%.5s' tag=%d%n", s, sig4, sig5, tag);
        }
        
        // Try sector 256 (standard AVDP)
        System.out.println("\n=== Sector 256 (standard AVDP) ===");
        byte[] avdp = readSector(256);
        if (avdp != null) {
            System.out.printf("First 64 bytes: %n");
            for (int i = 0; i < 64; i++) {
                System.out.printf("%02X ", avdp[i]);
                if ((i+1) % 16 == 0) System.out.println();
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
}
