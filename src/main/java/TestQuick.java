import java.io.*;
import java.net.*;

public class TestQuick {
    public static void main(String[] args) throws Exception {
        String url = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        int code = conn.getResponseCode();
        System.out.println("Redirect code: " + code);
        if (code == 302) {
            String loc = conn.getHeaderField("Location");
            System.out.println("Location: " + loc);
            conn.disconnect();
            
            // 读取sector 256 (AVDP)
            conn = (HttpURLConnection) new URL(loc).openConnection();
            conn.setRequestProperty("Range", "bytes=" + (256L * 2048) + "-" + (256L * 2048 + 2047));
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            System.out.println("Response: " + conn.getResponseCode());
            
            InputStream is = conn.getInputStream();
            byte[] data = is.readAllBytes();
            System.out.println("Read " + data.length + " bytes");
            
            // 检查tag id
            int tagId = data[0] | (data[1] << 8);
            System.out.println("Tag ID: " + tagId + " (expected 2 for AVDP)");
            
            // 检查签名
            String sig = new String(data, 4, 5);
            System.out.println("Signature: " + sig);
            
            // 检查NSR
            if (tagId == 2) {
                long mainVDS = readUInt32LE(data, 16);
                System.out.println("Main VDS at sector: " + mainVDS);
            }
        }
    }
    
    static long readUInt32LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
}
