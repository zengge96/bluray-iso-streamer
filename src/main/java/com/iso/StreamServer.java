package com.iso;

import com.iso.udf.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class StreamServer {
    static UdfParser parser;
    
    public static void main(String[] args) throws Exception {
        String url = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        parser = new UdfParser(url);
        parser.parse();
        
        System.out.println("Parsed " + parser.getFiles().size() + " files");
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/files", ex -> {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"files\":[");
            List<IsoFile> files = parser.getFiles();
            for (int i = 0; i < Math.min(files.size(), 500); i++) {
                IsoFile f = files.get(i);
                sb.append("{\"name\":\"").append(f.name.replace("\"", "\\\""))
                  .append("\",\"sector\":").append(f.sector).append("}");
                if (i < files.size() - 1) sb.append(",");
            }
            sb.append("],\"total\":").append(files.size()).append("}");
            
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, sb.length());
            OutputStream os = ex.getResponseBody();
            os.write(sb.toString().getBytes());
            os.close();
        });
        
        server.createContext("/stream", ex -> {
            String query = ex.getRequestURI().getQuery();
            String sectorStr = null;
            if (query != null && query.startsWith("sector=")) {
                sectorStr = query.substring(7);
            }
            
            if (sectorStr == null) {
                ex.sendResponseHeaders(400, -1);
                return;
            }
            
            try {
                long sector = Long.parseLong(sectorStr);
                byte[] data = parser.readSectors(sector, 40);  // Read 40 sectors ~80KB
                ex.getResponseHeaders().set("Content-Type", "video/mp2t");
                ex.getResponseHeaders().set("Accept-Ranges", "bytes");
                ex.sendResponseHeaders(200, data.length);
                OutputStream os = ex.getResponseBody();
                os.write(data);
                os.close();
            } catch (Exception e) {
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        server.createContext("/", ex -> {
            String html = "<html><head><title>Blu-ray ISO Streamer</title></head><body>" +
                "<h1>🦕 Jurassic World Blu-ray ISO Streamer</h1>" +
                "<p>Total files: " + parser.getFiles().size() + "</p>" +
                "<h2>📁 Directories</h2><ul>";
            
            for (IsoFile f : parser.getFiles()) {
                if (!f.name.contains(".")) {
                    html += "<li>" + f.name + "</li>";
                }
            }
            html += "</ul><h2>🎬 m2ts Videos</h2><ul>";
            
            int count = 0;
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".m2ts") && count < 50) {
                    html += "<li><a href='/stream?sector=" + f.sector + "' target='_blank'>" + f.name + "</a> (sector " + f.sector + ")</li>";
                    count++;
                }
            }
            html += "</ul></body></html>";
            
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, html.length());
            ex.getResponseBody().write(html.getBytes("utf-8"));
            ex.getResponseBody().close();
        });
        
        server.setExecutor(null);
        server.start();
        System.out.println("🌐 Server: http://localhost:8080");
        System.out.println("📁 Files: http://localhost:8080/files");
        System.out.println("🎬 Stream: http://localhost:8080/stream?sector=16777218");
    }
}
