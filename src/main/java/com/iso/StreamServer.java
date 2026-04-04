package com.iso;

import com.iso.udf.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class StreamServer {
    static UdfParser parser;
    static String isoUrl;
    
    public static void main(String[] args) throws Exception {
        isoUrl = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        parser = new UdfParser(isoUrl);
        parser.parse();
        
        System.out.println("Server ready. Files: " + parser.getFiles().size());
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/files", ex -> {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"files\":[");
            List<IsoFile> files = parser.getFiles();
            for (int i = 0; i < files.size(); i++) {
                IsoFile f = files.get(i);
                sb.append("{\"name\":\"").append(f.name).append("\",\"sector\":").append(f.sector).append("}");
                if (i < files.size() - 1) sb.append(",");
            }
            sb.append("]}");
            
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
            
            long sector = Long.parseLong(sectorStr);
            try {
                byte[] data = parser.readSectors(sector, 1);
                ex.getResponseHeaders().set("Content-Type", "video/mp2t");
                ex.getResponseHeaders().set("Accept-Ranges", "bytes");
                ex.sendResponseHeaders(200, data.length);
                OutputStream os = ex.getResponseBody();
                os.write(data);
                os.close();
            } catch (Exception e) {
                ex.sendResponseHeaders(500, -1);
            }
        });
        
        server.createContext("/", ex -> {
            String html = "<html><body>" +
                "<h1>Blu-ray ISO Streamer</h1>" +
                "<p>Files: " + parser.getFiles().size() + "</p>" +
                "<h2>Sample m2ts files:</h2>" +
                "<ul>";
            
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".m2ts") && html.length() < 8000) {
                    html += "<li><a href='/stream?sector=" + f.sector + "'>" + f.name + "</a></li>";
                }
            }
            html += "</ul></body></html>";
            
            ex.getResponseHeaders().set("Content-Type", "text/html");
            ex.sendResponseHeaders(200, html.length());
            ex.getResponseBody().write(html.getBytes());
            ex.getResponseBody().close();
        });
        
        server.setExecutor(null);
        server.start();
        System.out.println("HTTP server on port 8080");
    }
}
