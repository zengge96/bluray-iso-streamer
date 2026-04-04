import java.io.*;
import java.net.*;

public class TestPart {
    static String finalUrl = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // 扫描32768开始的VDS
        for (long sector = 32768; sector < 32768 + 32; sector++) {
            byte[] vd = readSector(sector);
            if (vd == null || vd.length < 4) continue;
            
            int tagId = vd[0] | (vd[1] << 8);
            if (tagId == 5) { // Partition Descriptor
                System.out.println("Found Partition Descriptor at sector " + sector);
                long partLoc = readUInt32LE(vd, 188);
                System.out.println("Partition Location: " + partLoc);
                
                // 读取partition+256找FSD
                byte[] fsd = readSector(partLoc + 256);
                if (fsd != null) {
                    String sig = new String(fsd, 4, 5);
                    System.out.println("Sector " + (partLoc+256) + " signature: " + sig);
                }
                
                // 找root directory
                long rootDir = readUInt32LE(vd, 216);
                System.out.println("Root Directory ICB: sector " + rootDir);
                break;
            }
        }
    }
    
    static byte[] readSector(long sector) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
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
