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
        
        // 首页 - 文件浏览器
        server.createContext("/", ex -> {
            try { showRoot(ex); } 
            catch (Exception e) { ex.sendResponseHeaders(500, 0); }
        });
        
        // 文件列表 API
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
        
        // 播放: /play/STREAM/00007.m2ts
        server.createContext("/play", ex -> handlePlayRequest(ex, false));
        
        // 下载: /download/STREAM/00007.m2ts  
        server.createContext("/download", ex -> handlePlayRequest(ex, true));
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server: http://localhost:8080");
    }
    
    // 根据文件名查找文件
    static IsoFile findFile(String path) {
        // 清理路径
        if (path.startsWith("/")) path = path.substring(1);
        
        // 直接匹配
        for (IsoFile f : parser.getFiles()) {
            if (f.name.equals(path)) return f;
        }
        
        // 短文件名匹配
        for (IsoFile f : parser.getFiles()) {
            String shortName = f.name.contains("/") ? f.name.substring(f.name.lastIndexOf("/") + 1) : f.name;
            if (shortName.equals(path)) return f;
        }
        
        return null;
    }
    
    // 处理播放/下载请求
    static void handlePlayRequest(HttpExchange ex, boolean isDownload) {
        String path = ex.getRequestURI().getPath();
        
        // 移除 /play 或 /download 前缀
        if (path.startsWith("/play")) path = path.substring(5);
        else if (path.startsWith("/download")) path = path.substring(9);
        
        if (path.isEmpty() || path.equals("/")) {
            try { ex.sendResponseHeaders(400, 0); } catch (Exception e) {}
            return;
        }
        
        try {
            // URL 解码
            path = URLDecoder.decode(path, "utf-8");
            
            // 查找文件
            IsoFile f = findFile(path);
            if (f == null) {
                ex.getResponseHeaders().set("Content-Type", "text/plain");
                ex.sendResponseHeaders(404, 0);
                ex.getResponseBody().close();
                return;
            }
            
            long sector = f.sector;
            long fileSize = f.size;
            String filename = f.name.substring(f.name.lastIndexOf("/") + 1);
            
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
            
            // 读取数据 (M2TS 从偏移 4 开始)
            byte[] data = parser.readFileRange(sector, start == 0 ? 4 : start, (int)(contentLength - 4));
            if (data == null) data = new byte[0];
            
            // 设置响应头
            ex.getResponseHeaders().set("Content-Type", "video/mp2t");
            ex.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
            ex.getResponseHeaders().set("Accept-Ranges", "bytes");
            if (!isDownload) {
                ex.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + filename + "\"");
            }
            if (rangeHeader != null) {
                ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            }
            
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
            "<html>\n<head>\n" +
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
            ".file-header { display: grid; grid-template-columns: 1fr 120px 180px; gap: 10px; padding: 15px 20px; background: rgba(0,0,0,0.3); font-weight: bold; color: #888; border-bottom: 1px solid rgba(255,255,255,0.1); }\n" +
            ".file-row { display: grid; grid-template-columns: 1fr 120px 180px; gap: 10px; padding: 12px 20px; align-items: center; border-bottom: 1px solid rgba(255,255,255,0.05); transition: background 0.2s; }\n" +
            ".file-row:hover { background: rgba(255,255,255,0.1); }\n" +
            ".file-name { font-family: monospace; font-size: 1.1em; }\n" +
            ".file-size { color: #888; font-family: monospace; }\n" +
            ".btn-group { display: flex; gap: 8px; }\n" +
            ".btn { padding: 6px 16px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9em; text-decoration: none; display: inline-block; transition: all 0.2s; }\n" +
            ".btn-play { background: #4caf50; color: white; }\n" +
            ".btn-play:hover { background: #45a049; }\n" +
            ".btn-download { background: #2196f3; color: white; }\n" +
            ".btn-download:hover { background: #1976d2; }\n" +
            ".btn:hover { transform: translateY(-2px); }\n" +
            "@media (max-width: 768px) {\n" +
            "  .file-header, .file-row { grid-template-columns: 1fr 80px 120px; }\n" +
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
            "    <span>Actions</span>\n" +
            "  </div>\n";
        
        for (IsoFile f : m2tsFiles) {
            // 简单 URL：只需要文件名
            String playUrl = "/play/" + f.name;
            String downloadUrl = "/download/" + f.name;
            
            html += "  <div class='file-row'>\n" +
                "    <span class='file-name'>" + f.name + "</span>\n" +
                "    <span class='file-size'>" + formatSize(f.size) + "</span>\n" +
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