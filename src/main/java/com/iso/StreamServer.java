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
        
        // Home - file browser
        server.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getPath();
                if (path.equals("/")) path = "/BDMV";
                
                String html = "<!DOCTYPE html><html><head>" +
                    "<meta charset='utf-8'><title>Blu-ray ISO Browser</title>" +
                    "<style>body{font-family:monospace;padding:20px}" +
                    "h1{color:#333}h2{color:#666;margin-top:30px}" +
                    "a{text-decoration:none;color:#0066cc}" +
                    "a:hover{text-decoration:underline}" +
                    "ul{list-style:none;padding:0}" +
                    "li{padding:5px 0;border-bottom:1px solid #eee}" +
                    ".dir{color:#ff6600}.file{color:#0066cc}" +
                    ".size{color:#999;font-size:0.9em}</style>" +
                    "</head><body>" +
                    "<h1>🦕 Jurassic World Blu-ray ISO</h1>" +
                    "<p>Total files: " + parser.getFiles().size() + " | " +
                    "<a href='/files'>JSON API</a> | " +
                    "<a href='/info'>Info</a></p>";
                
                // Build directory tree
                Map<String, List<IsoFile>> dirs = new TreeMap<>();
                for (IsoFile f : parser.getFiles()) {
                    String dir = "/";
                    String name = f.name;
                    int slash = name.lastIndexOf('/');
                    if (slash > 0) {
                        dir = name.substring(0, slash);
                        name = name.substring(slash + 1);
                    }
                    dirs.computeIfAbsent(dir, k -> new ArrayList<>()).add(f);
                }
                
                // Show root directories
                html += "<h2>📁 Root Directory</h2><ul>";
                Set<String> rootDirs = new TreeSet<>();
                for (String d : dirs.keySet()) {
                    if (d.equals("/")) {
                        for (IsoFile f : dirs.get(d)) {
                            String fname = f.name;
                            boolean isDir = !fname.contains(".");
                            if (isDir) {
                                html += "<li class='dir'>📁 <a href='/" + fname + "/'>" + fname + "</a></li>";
                            }
                        }
                    }
                }
                html += "</ul>";
                
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                byte[] data = html.getBytes("utf-8");
                ex.sendResponseHeaders(200, data.length);
                ex.getResponseBody().write(data);
                ex.getResponseBody().close();
            } catch (Exception e) {
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        // Directory browsing
        server.createContext("/BDMV", ex -> {
            try {
                String path = ex.getRequestURI().getPath();
                String html = browseDirectory(path);
                
                byte[] data = html.getBytes("utf-8");
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, data.length);
                ex.getResponseBody().write(data);
                ex.getResponseBody().close();
            } catch (Exception e) {
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        // Files JSON API
        server.createContext("/files", ex -> {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"files\":[");
            List<IsoFile> files = parser.getFiles();
            for (int i = 0; i < files.size(); i++) {
                IsoFile f = files.get(i);
                sb.append("{\"name\":\"").append(f.name.replace("\"", "\\\""))
                  .append("\",\"sector\":").append(f.sector).append("}");
                if (i < files.size() - 1) sb.append(",");
            }
            sb.append("],\"total\":").append(files.size()).append("}");
            
            String resp = sb.toString();
            byte[] data = resp.getBytes("utf-8");
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data);
            ex.getResponseBody().close();
        });
        
        // Stream/Download
        server.createContext("/stream", ex -> {
            String query = ex.getRequestURI().getQuery();
            String sectorStr = null;
            if (query != null && query.startsWith("sector=")) {
                sectorStr = query.substring(7);
            }
            
            if (sectorStr == null) {
                ex.sendResponseHeaders(400, 0);
                return;
            }
            
            try {
                long sector = Long.parseLong(sectorStr);
                byte[] data = parser.readSectors(sector, 100);  // 200KB
                ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
                ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=stream.ts");
                ex.sendResponseHeaders(200, data.length);
                OutputStream os = ex.getResponseBody();
                os.write(data);
                os.close();
            } catch (Exception e) {
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        // Direct file download by sector
        server.createContext("/download", ex -> {
            String query = ex.getRequestURI().getQuery();
            String sectorStr = null, filename = "file";
            if (query != null && query.contains("sector=")) {
                int s = query.indexOf("sector=") + 7;
                int e = query.indexOf("&", s);
                if (e < 0) e = query.length();
                sectorStr = query.substring(s, e);
            }
            if (query != null && query.contains("name=")) {
                int s = query.indexOf("name=") + 5;
                filename = query.substring(s);
                filename = URLDecoder.decode(filename, "utf-8");
            }
            
            if (sectorStr == null) {
                ex.sendResponseHeaders(400, 0);
                return;
            }
            
            try {
                long sector = Long.parseLong(sectorStr);
                byte[] data = parser.readSectors(sector, 200);  // 400KB
                ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
                ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                ex.sendResponseHeaders(200, data.length);
                OutputStream os = ex.getResponseBody();
                os.write(data);
                os.close();
            } catch (Exception e) {
                ex.sendResponseHeaders(500, 0);
            }
        });
        
        // Info
        server.createContext("/info", ex -> {
            String info = "Blu-ray ISO Streamer\n" +
                "Files: " + parser.getFiles().size() + "\n" +
                "Partition: " + parser.getPartitionStart() + "\n" +
                "Example: /download?sector=16777218&name=00001.m2ts";
            
            byte[] data = info.getBytes("utf-8");
            ex.getResponseHeaders().set("Content-Type", "text/plain");
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data);
            ex.getResponseBody().close();
        });
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server: http://localhost:8080");
        System.out.println("Files: http://localhost:8080/files");
    }
    
    static String browseDirectory(String path) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>" +
            "<meta charset='utf-8'><title>").append(path).append("</title>" +
            "<style>body{font-family:monospace;padding:20px}" +
            "a{text-decoration:none;color:#0066cc}" +
            "li{padding:8px 0;border-bottom:1px solid #eee}" +
            ".dir{color:#ff6600;font-weight:bold}</style>" +
            "</head><body>" +
            "<h1>📁 ").append(path).append("</h1>" +
            "<p><a href='/'>🏠 Root</a></p><ul>");
        
        // Group files by directory
        Map<String, List<IsoFile>> dirs = new TreeMap<>();
        List<IsoFile> files = new ArrayList<>();
        
        for (IsoFile f : parser.getFiles()) {
            String dir = "/";
            String name = f.name;
            int slash = name.lastIndexOf('/');
            if (slash > 0) {
                dir = name.substring(0, slash);
                name = name.substring(slash + 1);
            }
            
            if (dir.equals(path) || (path.equals("/BDMV") && dir.equals("/BDMV"))) {
                if (name.contains(".")) {
                    files.add(f);
                }
            }
        }
        
        // Show directories first
        for (String d : new TreeSet<>(dirs.keySet())) {
            if (d.equals(path) || (path.equals("/BDMV") && d.equals("/BDMV"))) {
                // Show subdirs
            }
        }
        
        // Show files
        for (IsoFile f : files) {
            String fname = f.name;
            int slash = fname.lastIndexOf('/');
            if (slash > 0) fname = fname.substring(slash + 1);
            
            String ext = "";
            int dot = fname.lastIndexOf('.');
            if (dot > 0) ext = fname.substring(dot);
            
            html.append("<li>📄 <a href='/download?sector=").append(f.sector)
                .append("&name=").append(fname).append("'>").append(fname).append("</a>")
                .append(" <span class='size'>[sector: ").append(f.sector).append("]</span></li>");
        }
        
        html.append("</ul></body></html>");
        return html.toString();
    }
}
