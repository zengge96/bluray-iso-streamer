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
                if (path.equals("/")) {
                    showRoot(ex);
                } else {
                    showDirectory(ex, path);
                }
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
                  .append("\",\"sector\":").append(f.sector)
                  .append(",\"size\":").append(f.size).append("}");
                if (i < files.size() - 1) sb.append(",");
            }
            sb.append("],\"total\":").append(files.size()).append("}");
            
            byte[] data = sb.toString().getBytes("utf-8");
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data);
            ex.getResponseBody().close();
        });
        
        // Download/Stream - supports full file and Range requests
        server.createContext("/download", ex -> {
            handleFileRequest(ex, false);
        });
        
        // Stream endpoint (alias for download)
        server.createContext("/stream", ex -> {
            handleFileRequest(ex, true);
        });
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server: http://localhost:8080");
    }
    
    static void handleFileRequest(HttpExchange ex, boolean isStream) {
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
            try { filename = URLDecoder.decode(filename, "utf-8"); } catch (Exception e) {}
        }
        
        if (sectorStr == null) {
            try { ex.sendResponseHeaders(400, 0); } catch (Exception e) {}
            return;
        }
        
        try {
            long sector = Long.parseLong(sectorStr);
            
            // Find the file to get its actual size
            long fileSize = 0;
            for (IsoFile f : parser.getFiles()) {
                if (f.name.equals(filename) || f.name.replace(".m2ts", ".mts").equals(filename)) {
                    fileSize = f.size;
                    break;
                }
            }
            
            // Default size if not found (100MB)
            if (fileSize <= 0) fileSize = 100 * 1024 * 1024;
            
            // Parse Range header
            String rangeHeader = ex.getRequestHeaders().getFirst("Range");
            long start = 0;
            long end = fileSize - 1;
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String range = rangeHeader.substring(6);
                int dash = range.indexOf("-");
                if (dash >= 0) {
                    if (dash > 0) start = Long.parseLong(range.substring(0, dash));
                    if (dash < range.length() - 1) end = Long.parseLong(range.substring(dash + 1));
                }
            }
            
            // Limit end to file size
            end = Math.min(end, fileSize - 1);
            long contentLength = end - start + 1;
            
            // Read the requested range
            byte[] data = parser.readFileRange(sector, start, contentLength);
            
            if (data == null) data = new byte[0];
            
            // Set headers
            ex.getResponseHeaders().set("Content-Type", "video/mp2t");
            ex.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
            ex.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + filename + "\"");
            ex.getResponseHeaders().set("Accept-Ranges", "bytes");
            ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            
            int status = (rangeHeader != null) ? 206 : 200;
            ex.sendResponseHeaders(status, data.length);
            
            OutputStream os = ex.getResponseBody();
            os.write(data);
            os.close();
            
        } catch (Exception e) {
            try { ex.sendResponseHeaders(500, 0); } catch (Exception ex2) {}
        }
    }
    
    static void showRoot(HttpExchange ex) throws Exception {
        String html = "<!DOCTYPE html><html><head>" +
            "<meta charset='utf-8'><title>Blu-ray ISO</title>" +
            "<style>body{font-family:monospace;padding:20px;background:#f5f5f5}" +
            "h1{color:#333}h2{color:#666;margin-top:20px}" +
            "a{text-decoration:none;color:#0066cc;display:block;padding:8px}" +
            "a:hover{background:#e0e0e0;border-radius:4px}" +
            "ul{background:white;padding:20px;border-radius:8px;list-style:none;box-shadow:0 2px 4px rgba(0,0,0,0.1)}" +
            "li{padding:5px 0;border-bottom:1px solid #eee}.dir:before{content:'📁 '}" +
            "table{width:100%;border-collapse:collapse}td,th{padding:8px;text-align:left;border-bottom:1px solid #eee}" +
            "th{background:#f0f0f0}.size{color:#888;font-size:0.9em}</style></head><body>" +
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
            "li{padding:5px 0;border-bottom:1px solid #eee}.back{color:#666;margin-bottom:10px}" +
            "table{width:100%;border-collapse:collapse}td,th{padding:8px;text-align:left;border-bottom:1px solid #eee}" +
            "th{background:#f0f0f0}.size{color:#888;font-size:0.9em}</style></head><body>" +
            "<h1>📁 /" + dirName + "/</h1>" +
            "<a class='back' href='/'>⬆️ Back to Root</a>" +
            "<h2>📄 Files in this directory</h2><table><tr><th>Name</th><th>Size</th><th>Sector</th></tr>";
        
        List<IsoFile> showFiles = new ArrayList<>();
        
        if (dirName.equalsIgnoreCase("STREAM")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".m2ts")) showFiles.add(f);
            }
        } else if (dirName.equalsIgnoreCase("PLAYLIST")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".mpls")) showFiles.add(f);
            }
        } else if (dirName.equalsIgnoreCase("CLIPINF")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".clpi")) showFiles.add(f);
            }
        } else if (dirName.equalsIgnoreCase("BDJO")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".bdjo")) showFiles.add(f);
            }
        } else if (dirName.equalsIgnoreCase("JAR")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".jar")) showFiles.add(f);
            }
        } else if (dirName.equalsIgnoreCase("META")) {
            for (IsoFile f : parser.getFiles()) {
                if (f.name.endsWith(".xml") || f.name.endsWith(".jpg")) showFiles.add(f);
            }
        }
        
        showFiles.sort(Comparator.comparing(f -> f.name));
        
        for (IsoFile f : showFiles) {
            String fname = f.name;
            html += "<tr><td><a href='/download?sector=" + f.sector + "&name=" + fname + "'>" + fname + "</a></td>";
            html += "<td class='size'>" + formatSize(f.size) + "</td>";
            html += "<td class='size'>" + f.sector + "</td></tr>";
        }
        
        html += "</table></body></html>";
        
        byte[] data = html.getBytes("utf-8");
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }
    
    static String formatSize(long size) {
        if (size <= 0) return "-";
        if (size < 1024) return size + " B";
        if (size < 1024*1024) return String.format("%.1f KB", size/1024.0);
        if (size < 1024*1024*1024) return String.format("%.1f MB", size/(1024.0*1024));
        return String.format("%.2f GB", size/(1024.0*1024*1024));
    }
}
