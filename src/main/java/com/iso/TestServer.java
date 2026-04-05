package com.iso;
import com.iso.udf.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;

public class TestServer {
    public static void main(String[] args) throws Exception {
        String url = new BufferedReader(new FileReader("/root/.openclaw/workspace/url.txt")).readLine().trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        System.out.println("Files: " + parser.getFiles().size());
        
        // Show first m2ts
        for (IsoFile f : parser.getFiles()) {
            if (f.name.endsWith(".m2ts")) {
                System.out.println("First m2ts: " + f.name + " sector=" + f.sector + " size=" + f.size);
                break;
            }
        }
        
        // Test reading file data
        for (IsoFile f : parser.getFiles()) {
            if (f.name.equals("00007.m2ts")) {
                byte[] data = parser.readSectors(f.sector, 1);
                System.out.println("Data at sector " + f.sector + ": " + 
                    String.format("%02x %02x %02x %02x", data[4], data[5], data[6], data[7]));
                if (data[4] == 0x47) {
                    System.out.println("✓ TS data ready for streaming!");
                }
                break;
            }
        }
        
        // Start simple HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/files", ex -> {
            StringBuilder sb = new StringBuilder("[");
            var files = parser.getFiles();
            for (int i = 0; i < Math.min(20, files.size()); i++) {
                IsoFile f = files.get(i);
                sb.append("{\"name\":\"").append(f.name).append("\",\"sector\":").append(f.sector)
                  .append(",\"size\":").append(f.size).append("}");
                if (i < Math.min(20, files.size()) - 1) sb.append(",");
            }
            sb.append("]");
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, sb.length());
            ex.getResponseBody().write(sb.toString().getBytes());
        });
        
        server.createContext("/stream", ex -> {
            String q = ex.getRequestURI().getQuery();
            long sector = -1, size = 100_000_000, start = 0;
            
            if (q != null) {
                for (String p : q.split("&")) {
                    if (p.startsWith("sector=")) sector = Long.parseLong(p.substring(7));
                    if (p.startsWith("size=")) size = Long.parseLong(p.substring(5));
                    if (p.startsWith("start=")) start = Long.parseLong(p.substring(6));
                }
            }
            
            if (sector < 0) {
                ex.sendResponseHeaders(400, 0);
                return;
            }
            
            long end = Math.min(start + 1024*1024, size); // 1MB max
            byte[] data = parser.readFileRange(sector, start, (int)(end - start));
            
            ex.getResponseHeaders().set("Content-Type", "video/mp2t");
            ex.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data);
            System.out.println("Streamed sector=" + sector + " len=" + data.length);
        });
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server: http://localhost:8080");
    }
}
