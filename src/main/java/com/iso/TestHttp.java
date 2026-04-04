package com.iso;
import java.net.*;

public class TestHttp {
    public static void main(String[] args) throws Exception {
        URL url = new URL("http://localhost:8080/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        
        System.out.println("Response: " + conn.getResponseCode());
        System.out.println("Content-Type: " + conn.getContentType());
        
        java.io.InputStream is = conn.getInputStream();
        byte[] buf = new byte[4096];
        int n = is.read(buf);
        System.out.println("Read " + n + " bytes");
        System.out.println(new String(buf, 0, n, "utf-8"));
    }
}
