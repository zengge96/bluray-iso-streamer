package com.iso;
import java.net.*;
import java.io.*;

public class DebugRedirect {
    public static void main(String[] args) throws Exception {
        String urlStr = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);  // Should auto-follow
        conn.setConnectTimeout(20000);
        
        System.out.println("Request URL: " + url);
        System.out.println("Response Code: " + conn.getResponseCode());
        System.out.println("Content-Type: " + conn.getContentType());
        System.out.println("Location: " + conn.getHeaderField("Location"));
        
        // Read first bytes
        InputStream is = conn.getInputStream();
        byte[] buf = new byte[32];
        int n = is.read(buf);
        System.out.print("First bytes: ");
        for (int i = 0; i < n; i++) System.out.printf("%02X ", buf[i]&0xFF);
        System.out.println();
    }
}
