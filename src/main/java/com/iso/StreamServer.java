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
        
        server.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getPath();
                if (path.equals("/")) {
                    showRoot(ex);
                } else {
                    showDirectory(ex, path);
                }
            } catch (Exception e) {
                ex.sendResponseHeaders(500, 0);
            }
        });
        
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
            
            byte[] data = sb.toString().getBytes("utf-8");
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data);
            ex.getResponseBody().close();
        });
        
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
                byte[] data = parser.readSectors(sector, 200);
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
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server: http://localhost:8080");
    }
    
    static void showRoot(HttpExchange ex) throws Exception {
        String html = "<!DOCTYPE html><html><head>" +
            "<meta charset='utf-8'><title>Blu-ray ISO</title>" +
            "<style>body{font-family:monospace;padding:20px;background:#f5f5f5}" +
            "h1{color:#333}h2{color:#666}" +
            "a{text-decoration:none;color:#0066cc;display:block;padding:8px}" +
            "a:hover{background:#e0e0e0;border-radius:4px}" +
            "ul{background:white;padding:20px;border-radius:8px;list-style:none;box-shadow:0 2px 4px rgba(0,0,0,0.1)}" +
            "li{padding:5px 0;border-bottom:1px solid #eee}.dir:before{content:'📁 '}</style></head><body>" +
            "<h1>🦕 Jurassic World Blu-ray ISO</h1>" +
            "<p>Total files: " + parser.getFiles().size() + " | <a href='/files'>JSON</a></p>" +
            "<h2>📁 Root Directory</h2><ul>";
        
        Set<String> rootDirs = new TreeSet<>();
        for (IsoFile f : parser.getFiles()) {
            if (!f.name.contains("/")) {
                rootDirs.add(f.name);
            }
        }
        
        for (String d : rootDirs) {
            boolean isDir = !d.contains(".");
            if (isDir) {
                html += "<li class='dir'><a href='/" + d + "/'>" + d + "</a></li>";
            }
        }
        
        html += "</ul></body></html>";
        
        byte[] data = html.getBytes("utf-8");
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }
    
    static void showDirectory(HttpExchange ex, String path) throws Exception {
        String dirName = path;
        if (dirName.startsWith("/")) dirName = dirName.substring(1);
        if (dirName.endsWith("/")) dirName = dirName.substring(0, dirName.length() - 1);
        
        String html = "<!DOCTYPE html><html><head>" +
            "<meta charset='utf-8'><title>" + dirName + "</title>" +
            "<style>body{font-family:monospace;padding:20px;background:#f5f5f5}" +
            "a{text-decoration:none;color:#0066cc;display:block;padding:8px}" +
            "a:hover{background:#e0e0e0;border-radius:4px}" +
            "ul{background:white;padding:20px;border-radius:8px;list-style:none;box-shadow:0 2px 4px rgba(0,0,0,0.1)}" +
            "li{padding:5px 0;border-bottom:1px solid #eee}.back{color:#666;margin-bottom:10px}</style></head><body>" +
            "<h1>📁 /" + dirName + "/</h1>" +
            "<a class='back' href='/'>⬆️ Back to Root</a>" +
            "<h2>📄 Files in this directory</h2><ul>";
        
        // Show all files that might be in this directory (by extension pattern)
        // Since we don't have full paths, show files by type
        String targetDir = "/" + dirName + "/";
        
        List<IsoFile> showFiles = new ArrayList<>();
        
        // For STREAM directory, show m2ts files
        if (dirName.equalsIgnoreCase("STREAM")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".m2ts")) {
                    showFiles.add(f);
                }
            }
        } else if (dirName.equalsIgnoreCase("PLAYLIST")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".mpls")) {
                    showFiles.add(f);
                }
            }
        } else if (dirName.equalsIgnoreCase("CLIPINF")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".clpi")) {
                    showFiles.add(f);
                }
            }
        } else if (dirName.equalsIgnoreCase("BDJO")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".bdjo")) {
                    showFiles.add(f);
                }
            }
        } else if (dirName.equalsIgnoreCase("JAR")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".jar")) {
                    showFiles.add(f);
                }
            }
        } else if (dirName.equalsIgnoreCase("META")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".xml") || f.name.endsWith(".jpg")) {
                    showFiles.add(f);
                }
            }
        } else {
            // Default: show files matching directory name pattern or all files
            for (IsoFile f : parser.getFiles()) {
                if (f.name.toLowerCase().contains(dirName.toLowerCase())) {
                    showFiles.add(f);
                }
            }
        }
        
        // Sort and show
        showFiles.sort(Comparator.comparing(f -> f.name));
        
        for (IsoFile f : showFiles) {
            String fname = f.name;
            // Use a default name based on type if no full path
            if (dirName.equalsIgnoreCase("STREAM")) {
                fname = "00007.m2ts".replace("00007", fname.replace(".m2ts", ""));
            }
            html += "<li><a href='/download?sector=" + f.sector + "&name=" + fname + "'>" + f.name + "</a> [" + f.sector + "]</li>";
        }
        
        if (showFiles.isEmpty()) {
            html += "<li>No " + dirName + " files found - showing all files</li>";
            // Show all files as fallback
            for (IsoFile f : parser.getFiles()) {
                html += "<li><a href='/download?sector=" + f.sector + "&name=" + f.name + "'>" + f.name + "</a> [" + f.sector + "]</li>";
            }
        }
        
        html += "</ul></body></html>";
        
        byte[] data = html.getBytes("utf-8");
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }
}
