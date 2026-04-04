package com.iso;
import java.net.*;
import java.io.*;

public class DebugNet {
    public static void main(String[] args) throws Exception {
        String urlStr = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        System.out.println("Testing URL: " + urlStr);
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        
        // Try to read from different offsets
        long[] offsets = {0, 524288, 589824, 655360};
        
        for (long off : offsets) {
            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Range", "bytes=" + off + "-" + (off + 31));
            conn.setConnectTimeout(20000);
            
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[32];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
                if (baos.size() >= 32) break;
            }
            is.close();
            
            byte[] data = baos.toByteArray();
            if (data.length >= 2) {
                int tag = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
                System.out.printf("Offset %d: tag=%d, bytes=%02X%02X...%n", off, tag, data[0]&0xFF, data[1]&0xFF);
            }
        }
    }
}
