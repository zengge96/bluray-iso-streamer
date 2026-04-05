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
        
        // Home - 文件浏览器
        server.createContext("/", ex -> {
            try {
                showRoot(ex);
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
        
        // Download/Stream
        server.createContext("/download", ex -> handleFileRequest(ex, false));
        server.createContext("/stream", ex -> handleFileRequest(ex, true));
        
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
            long fileSize = 0;
            for (IsoFile f : parser.getFiles()) {
                // 匹配：完整路径或纯文件名
                String shortName = f.name.contains("/") ? f.name.substring(f.name.lastIndexOf("/") + 1) : f.name;
                if (f.name.equals(filename) || shortName.equals(filename) || 
                    f.name.replace(".m2ts", ".mts").equals(filename)) {
                    fileSize = f.size;
                    break;
                }
            }
            // 如果还是找不到，尝试从URL参数获取size
            if (fileSize <= 0 && query != null && query.contains("size=")) {
                int s = query.indexOf("size=") + 5;
                int e = query.indexOf("&", s);
                if (e < 0) e = query.length();
                try { fileSize = Long.parseLong(query.substring(s, e)); } catch (Exception e2) {}
            }
            if (fileSize <= 0) fileSize = 100 * 1024 * 1024;
            
            // Range 请求支持
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
            
            end = Math.min(end, fileSize - 1);
            long contentLength = end - start + 1;
            
            byte[] data = parser.readFileRange(sector, start, (int)contentLength);
            if (data == null) data = new byte[0];
            
            ex.getResponseHeaders().set("Content-Type", "video/mp2t");
            ex.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
            ex.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + filename + "\"");
            ex.getResponseHeaders().set("Accept-Ranges", "bytes");
            ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            
            int status = (rangeHeader != null) ? 206 : 200;
            ex.sendResponseHeaders(status, data.length);
            ex.getResponseBody().write(data);
            ex.getResponseBody().close();
            
        } catch (Exception e) {
            try { ex.sendResponseHeaders(500, 0); } catch (Exception ex2) {}
        }
    }
    
    static void showRoot(HttpExchange ex) throws Exception {
        List<IsoFile> m2tsFiles = new ArrayList<>();
        for (IsoFile f : parser.getFiles()) {
            if (f.name.endsWith(".m2ts")) m2tsFiles.add(f);
        }
        m2tsFiles.sort(Comparator.comparing(f -> f.name));
        
        String html = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "<meta charset='utf-8'>\n" +
            "<title>🦕 Jurassic World Blu-ray</title>\n" +
            "<style>\n" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); min-height: 100vh; color: #fff; padding: 20px; }\n" +
            ".container { max-width: 1200px; margin: 0 auto; }\n" +
            "h1 { font-size: 2em; margin-bottom: 10px; display: flex; align-items: center; gap: 10px; }\n" +
            ".subtitle { color: #888; margin-bottom: 30px; }\n" +
            ".stats { display: flex; gap: 20px; margin-bottom: 30px; }\n" +
            ".stat { background: rgba(255,255,255,0.1); padding: 15px 25px; border-radius: 10px; }\n" +
            ".stat-value { font-size: 1.5em; font-weight: bold; color: #4fc3f7; }\n" +
            ".stat-label { color: #888; font-size: 0.9em; }\n" +
            ".file-list { background: rgba(255,255,255,0.05); border-radius: 15px; overflow: hidden; }\n" +
            ".file-header { display: grid; grid-template-columns: 1fr 120px 120px 180px; gap: 10px; padding: 15px 20px; background: rgba(0,0,0,0.3); font-weight: bold; color: #888; border-bottom: 1px solid rgba(255,255,255,0.1); }\n" +
            ".file-row { display: grid; grid-template-columns: 1fr 120px 120px 180px; gap: 10px; padding: 12px 20px; align-items: center; border-bottom: 1px solid rgba(255,255,255,0.05); transition: background 0.2s; }\n" +
            ".file-row:hover { background: rgba(255,255,255,0.1); }\n" +
            ".file-name { font-family: monospace; font-size: 1.1em; }\n" +
            ".file-size { color: #888; font-family: monospace; }\n" +
            ".file-sector { color: #666; font-family: monospace; font-size: 0.9em; }\n" +
            ".btn-group { display: flex; gap: 8px; }\n" +
            ".btn { padding: 6px 16px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9em; text-decoration: none; display: inline-block; transition: all 0.2s; }\n" +
            ".btn-play { background: #4caf50; color: white; }\n" +
            ".btn-play:hover { background: #45a049; }\n" +
            ".btn-download { background: #2196f3; color: white; }\n" +
            ".btn-download:hover { background: #1976d2; }\n" +
            ".btn:hover { transform: translateY(-2px); }\n" +
            ".progress { background: rgba(255,255,255,0.1); height: 4px; border-radius: 2px; margin-top: 5px; }\n" +
            ".progress-bar { background: linear-gradient(90deg, #4caf50, #8bc34a); height: 100%; border-radius: 2px; }\n" +
            "@media (max-width: 768px) {\n" +
            "  .file-header, .file-row { grid-template-columns: 1fr 80px 120px; }\n" +
            "  .file-sector { display: none; }\n" +
            "  .btn-group { flex-direction: column; }\n" +
            "}\n" +
            "</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div class='container'>\n" +
            "<h1>🦕 Jurassic World Blu-ray</h1>\n" +
            "<p class='subtitle'>UDF ISO Network Streamer</p>\n" +
            "\n" +
            "<div class='stats'>\n" +
            "  <div class='stat'><div class='stat-value'>" + m2tsFiles.size() + "</div><div class='stat-label'>Video Files</div></div>\n" +
            "  <div class='stat'><div class='stat-value'>" + formatSize(getTotalSize(m2tsFiles)) + "</div><div class='stat-label'>Total Size</div></div>\n" +
            "</div>\n" +
            "\n" +
            "<div class='file-list'>\n" +
            "  <div class='file-header'>\n" +
            "    <span>File Name</span>\n" +
            "    <span>Size</span>\n" +
            "    <span>Sector</span>\n" +
            "    <span>Actions</span>\n" +
            "  </div>\n";
        
        for (IsoFile f : m2tsFiles) {
            String playUrl = "/stream?sector=" + f.sector + "&size=" + f.size + "&name=" + f.name + "&start=4";
            String downloadUrl = "/download?sector=" + f.sector + "&size=" + f.size + "&name=" + f.name + "&start=4";
            
            html += "  <div class='file-row'>\n" +
                "    <span class='file-name'>" + f.name + "</span>\n" +
                "    <span class='file-size'>" + formatSize(f.size) + "</span>\n" +
                "    <span class='file-sector'>" + f.sector + "</span>\n" +
                "    <div class='btn-group'>\n" +
                "      <a class='btn btn-play' href='" + playUrl + "' target='_blank'>▶ Play</a>\n" +
                "      <a class='btn btn-download' href='" + downloadUrl + "' download>⬇ Download</a>\n" +
                "    </div>\n" +
                "  </div>\n";
        }
        
        html += "</div>\n" +
            "</div>\n" +
            "</body>\n" +
            "</html>";
        
        byte[] data = html.getBytes("utf-8");
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }
    
    static long getTotalSize(List<IsoFile> files) {
        long total = 0;
        for (IsoFile f : files) total += f.size;
        return total;
    }
    
    static String formatSize(long size) {
        if (size <= 0) return "-";
        if (size < 1024) return size + " B";
        if (size < 1024*1024) return String.format("%.1f KB", size/1024.0);
        if (size < 1024*1024*1024) return String.format("%.1f MB", size/(1024.0*1024));
        return String.format("%.2f GB", size/(1024.0*1024*1024));
    }
}