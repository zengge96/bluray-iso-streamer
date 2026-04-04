package com.iso;
import java.net.*;
import java.io.*;

public class TestNetDebug {
    public static void main(String[] args) throws Exception {
        String urlStr = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(10000);
        
        // Get first bytes
        conn.setRequestProperty("Range", "bytes=524288-524303");
        InputStream is = conn.getInputStream();
        
        byte[] buf = new byte[16];
        int n = is.read(buf);
        is.close();
        
        System.out.println("Read " + n + " bytes");
        System.out.print("Hex: ");
        for (int i = 0; i < n; i++) {
            System.out.printf("%02X ", buf[i] & 0xFF);
        }
        
        int tagId = ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF);
        System.out.println("\nTag ID: " + tagId + " (expected 2)");
    }
}
