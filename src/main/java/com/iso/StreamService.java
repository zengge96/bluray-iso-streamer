package com.iso;

import java.io.*;
import java.net.*;
import java.util.*;

public class StreamService {
    private static final int SECTOR_SIZE = 2048;
    private static String isoUrl;
    
    public static void main(String[] args) throws Exception {
        isoUrl = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        System.out.println("Testing connection and stream...");
        
        String finalUrl = getFinalUrl();
        System.out.println("Final URL: " + finalUrl);
        
        // 测试读取sector 600000的数据（约1.2GB偏移）
        System.out.println("\nTesting stream read from offset " + (600000L * SECTOR_SIZE) + "...");
        byte[] testData = readFromRemote(600000L * SECTOR_SIZE, 4096);
        System.out.println("Read " + testData.length + " bytes");
        
        if (testData.length > 0) {
            System.out.println("First 64 bytes:");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(64, testData.length); i++) {
                sb.append(String.format("%02x ", testData[i] & 0xFF));
                if ((i + 1) % 16 == 0) sb.append("\n");
            }
            System.out.println(sb);
            
            String found = new String(testData, "ISO-8859-1");
            if (found.contains("BDMV")) {
                System.out.println("Found BDMV in data!");
            }
        }
    }
    
    static String getFinalUrl() throws Exception {
        URL url = new URL(isoUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(30000);
        
        int code = conn.getResponseCode();
        System.out.println("Initial response: " + code);
        
        if (code == 302 || code == 301) {
            String location = conn.getHeaderField("Location");
            System.out.println("Redirect to: " + location);
            conn.disconnect();
            return location;
        }
        conn.disconnect();
        return isoUrl;
    }
    
    static byte[] readFromRemote(long offset, int length) throws Exception {
        String finalUrl = getFinalUrl();
        URL url = new URL(finalUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
        
        int responseCode = conn.getResponseCode();
        System.out.println("Stream Response: " + responseCode);
        
        if (responseCode == 206) {
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            is.close();
            conn.disconnect();
            return baos.toByteArray();
        } else if (responseCode == 200) {
            System.out.println("Server doesn't support Range");
            InputStream is = conn.getInputStream();
            long skipped = 0;
            while (skipped < offset) {
                long s = is.skip(offset - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            byte[] buffer = new byte[length];
            int read = is.read(buffer);
            conn.disconnect();
            return read > 0 ? Arrays.copyOf(buffer, read) : new byte[0];
        } else {
            System.out.println("Error: " + responseCode + " " + conn.getResponseMessage());
            conn.disconnect();
            return new byte[0];
        }
    }
}