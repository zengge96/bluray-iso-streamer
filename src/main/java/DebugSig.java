import java.io.*;
import java.net.*;

public class DebugSig {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    public static void main(String[] args) throws Exception {
        // 检查partition开头的sector
        System.out.println("=== Partition " + 589824 + " forward ===");
        for (long s = 589824; s < 589824 + 32; s++) {
            byte[] sd = readSector(s);
            if (sd != null && sd.length >= 24) {
                String sig = new String(sd, 4, 5).trim();
                System.out.println("Sector " + s + ": '" + sig + "'");
                
                // 如果找到BDMV目录（通常在根目录）
                if (sig.equals("NSR02") || sig.equals("NSR03")) {
                    System.out.println("  -> Found NSR, looking for FSD nearby...");
                    for (long f = s + 1; f < s + 8; f++) {
                        byte[] fsd = readSector(f);
                        if (fsd != null) {
                            String fsig = new String(fsd, 4, 5).trim();
                            System.out.println("  Sector " + f + ": '" + fsig + "'");
                        }
                    }
                    break;
                }
            }
        }
        
        // 检查sector 256 (AVDP)
        System.out.println("\n=== Sector 256 ===");
        byte[] avdp = readSector(256);
        if (avdp != null) {
            System.out.println("Sig: '" + new String(avdp, 4, 5).trim() + "'");
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
            byte[] data = is.readAllBytes();
            is.close();
            conn.disconnect();
            return data;
        } catch (Exception e) {
            return null;
        }
    }
}
