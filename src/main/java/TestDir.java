import java.io.*;
import java.net.*;

public class TestDir {
    static String url = "https://cdnfhnfile.115cdn.net/68952a1d6b20a170f0cbf289e616898de82e31bf/Jurassic_World_2015_ULTRA_HD.iso?t=1779858862&u=309891702&s=524288000&d=vip-795368560--1-0&c=2&f=&k=a706b87e64a0aa6f3bee8d767277c62d&us=5242880000&uc=10&v=1";
    
    // 根据7z输出，partition从589824开始
    static long PARTITION = 589824;
    
    public static void main(String[] args) throws Exception {
        // 尝试partition+256找FSD或根目录
        System.out.println("=== Scanning partition+256 area ===");
        for (long s = PARTITION + 256; s < PARTITION + 300; s++) {
            byte[] data = readSector(s);
            if (data == null || data.length < 16) continue;
            
            // 检查File Entry tag (261) 或 NSR
            int tag = data[0] | (data[1] << 8);
            String sig = new String(data, 4, 5);
            
            if (tag == 261 || sig.startsWith("NSR") || sig.startsWith("FSD")) {
                System.out.println("Sector " + s + ": tag=" + tag + " sig='" + sig + "'");
                
                // 尝试解析为File Entry
                if (tag == 261) {
                    long infoLen = readUInt64LE(data, 80);
                    System.out.println("  Info Length: " + infoLen);
                }
                
                // 显示前64字节
                System.out.print("  Hex: ");
                for (int i = 0; i < Math.min(64, data.length); i++) {
                    System.out.printf("%02X ", data[i]);
                }
                System.out.println();
            }
        }
        
        // 尝试更简单的方法 - 搜索包含 "BDMV" 字符串的sector
        System.out.println("\n=== Searching for 'BDMV' string ===");
        for (long s = PARTITION; s < PARTITION + 1000 && s < 600000; s += 16) {
            byte[] data = readSectors(s, 1);
            if (data == null) continue;
            
            String str = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            int idx = str.indexOf("BDMV");
            if (idx >= 0 && idx < 100) {
                System.out.println("Found BDMV at sector " + s + " offset " + idx);
                // 显示周围数据
                int start = Math.max(0, idx - 32);
                System.out.println("  Context: " + new String(data, start, 64, java.nio.charset.StandardCharsets.ISO_8859_1).replaceAll("[^\\p{Print}]", "."));
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
    
    static byte[] readSectors(long sector, int count) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Range", "bytes=" + (sector * 2048) + "-" + (sector * 2048 + count * 2048 - 1));
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() != 206) return null;
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
                if (baos.size() >= count * 2048) break;
            }
            is.close();
            conn.disconnect();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    static long readUInt64LE(byte[] data, int offset) {
        return readUInt32LE(data, offset) | (readUInt32LE(data, offset + 4) << 32);
    }
    
    static long readUInt32LE(byte[] data, int offset) {
        if (offset + 4 > data.length) return 0;
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
}
