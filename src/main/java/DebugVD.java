import java.io.*;
import java.net.*;

public class DebugVD {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // 检查AVDP sector 256
        System.out.println("=== Sector 256 (AVDP) ===");
        byte[] avdp = readSector(256);
        if (avdp != null) {
            int tag = avdp[0] | (avdp[1] << 8);
            System.out.println("Tag: " + tag);
            long mainVDS = readUInt32LE(avdp, 16);
            long backupVDS = readUInt32LE(avdp, 24);
            System.out.println("Main VDS: " + mainVDS + ", Backup VDS: " + backupVDS);
            
            // 尝试mainVDS位置
            System.out.println("\n=== Checking Main VDS sectors ===");
            for (long s = mainVDS; s < mainVDS + 16; s++) {
                byte[] vd = readSector(s);
                if (vd != null && vd.length >= 4) {
                    int t = vd[0] | (vd[1] << 8);
                    System.out.println("Sector " + s + ": tag=" + t);
                    if (t == 5) { // Partition
                        long partLoc = readUInt32LE(vd, 188);
                        System.out.println("  -> Partition location: " + partLoc);
                    }
                }
            }
        }
        
        // 直接检查partition 589824
        System.out.println("\n=== Checking partition start 589824 ===");
        for (long s = 589824; s < 589824 + 16; s++) {
            byte[] sd = readSector(s);
            if (sd != null && sd.length >= 4) {
                int t = sd[0] | (sd[1] << 8);
                System.out.println("Sector " + s + ": tag=" + t);
            }
        }
    }
    
    static byte[] readSector(long sector) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Range", "bytes=" + (sector * 2048) + "-" + (sector * 2048 + 2047));
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() != 206) return null;
            InputStream is = conn.getInputStream();
            byte[] data = is.readAllBytes();
            is.close();
            conn.disconnect();
            return data;
        } catch (Exception e) {
            return null;
        }
    }
    
    static long readUInt32LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
}
