package com.iso;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final int SECTOR_SIZE = 2048;
    private static String isoUrl;
    private static int httpPort = 8080;
    private static Map<String, IsoParser.FileEntry> fileMap;
    
    public static void main(String[] args) throws Exception {
        isoUrl = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        System.out.println("ISO URL loaded");
        
        if (args.length > 0) {
            httpPort = Integer.parseInt(args[0]);
        }
        
        // 初始化文件列表
        fileMap = IsoParser.getFileListFromRemote();
        System.out.println("Loaded " + fileMap.size() + " files");
        
        System.out.println("Starting HTTP server on port " + httpPort);
        
        HttpServer server = HttpServer.create(new InetSocketAddress(httpPort), 0);
        
        server.createContext("/", new IndexHandler());
        server.createContext("/files", new FileListHandler(fileMap));
        server.createContext("/download", new DownloadHandler(fileMap));
        server.createContext("/stream", new StreamHandler(fileMap));
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("Server started!");
    }
    
    // 首页 - 重定向到文件列表
    static class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Location", "/files");
            exchange.sendResponseHeaders(302, -1);
        }
    }
    
    // 文件列表
    static class FileListHandler implements HttpHandler {
        private final Map<String, IsoParser.FileEntry> files;
        
        FileListHandler(Map<String, IsoParser.FileEntry> files) {
            this.files = files;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><title>Bluray ISO Files</title>");
            sb.append("<style>body{font-family:monospace;margin:20px} a{color:#0066cc}</style></head><body>\n");
            sb.append("<h1>📀 Bluray ISO 文件列表</h1>\n");
            sb.append("<p>总文件数: ").append(files.size()).append("</p>\n");
            sb.append("<table border='1' cellpadding='5'>\n");
            sb.append("<tr><th>文件名</th><th>大小</th><th>操作</th></tr>\n");
            
            for (Map.Entry<String, IsoParser.FileEntry> e : files.entrySet()) {
                String name = e.getKey();
                long size = e.getValue().size;
                String sizeStr = formatSize(size);
                sb.append("<tr>");
                sb.append("<td>").append(name).append("</td>");
                sb.append("<td>").append(sizeStr).append("</td>");
                sb.append("<td>");
                sb.append(String.format("<a href='/stream?file=%s'>▶ 播放</a> | ", URLEncoder.encode(name, "UTF-8")));
                sb.append(String.format("<a href='/download?file=%s'>💾 下载</a>", URLEncoder.encode(name, "UTF-8")));
                sb.append("</td></tr>\n");
            }
            
            sb.append("</table></body></html>");
            
            byte[] response = sb.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
        
        String formatSize(long size) {
            if (size < 1024) return size + " B";
            if (size < 1024*1024) return String.format("%.1f KB", size/1024.0);
            if (size < 1024*1024*1024) return String.format("%.1f MB", size/(1024.0*1024));
            return String.format("%.2f GB", size/(1024.0*1024*1024));
        }
    }
    
    // 下载Handler
    static class DownloadHandler implements HttpHandler {
        private final Map<String, IsoParser.FileEntry> files;
        
        DownloadHandler(Map<String, IsoParser.FileEntry> files) {
            this.files = files;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("file=")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            
            String fileName = URLDecoder.decode(query.substring(5), "UTF-8");
            IsoParser.FileEntry entry = files.get(fileName);
            
            if (entry == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }
            
            System.out.println("Download: " + fileName + " (offset=" + entry.offset + ", size=" + entry.size + ")");
            
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(entry.size));
            exchange.sendResponseHeaders(200, entry.size);
            
            // 流式传输
            streamFile(entry, exchange.getResponseBody());
        }
    }
    
    // 流媒体Handler
    static class StreamHandler implements HttpHandler {
        private final Map<String, IsoParser.FileEntry> files;
        
        StreamHandler(Map<String, IsoParser.FileEntry> files) {
            this.files = files;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("file=")) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            
            String fileName = URLDecoder.decode(query.substring(5), "UTF-8");
            IsoParser.FileEntry entry = files.get(fileName);
            
            if (entry == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }
            
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            
            exchange.getResponseHeaders().set("Content-Type", "video/mp2t");
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                // Range请求
                String range = rangeHeader.substring(6);
                int dash = range.indexOf('-');
                long start = Long.parseLong(range.substring(0, dash));
                long end = entry.size - 1;
                if (!range.substring(dash + 1).isEmpty()) {
                    end = Math.min(Long.parseLong(range.substring(dash + 1)), entry.size - 1);
                }
                
                long contentLength = end - start + 1;
                exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + entry.size);
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));
                exchange.sendResponseHeaders(206, contentLength);
                
                System.out.println("Streaming " + fileName + " range " + start + "-" + end);
                streamFileRange(entry, start, contentLength, exchange.getResponseBody());
            } else {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(entry.size));
                exchange.sendResponseHeaders(200, entry.size);
                
                System.out.println("Streaming " + fileName + " (full)");
                streamFile(entry, exchange.getResponseBody());
            }
        }
    }
    
    // 流式传输整个文件
    private static void streamFile(IsoParser.FileEntry entry, OutputStream out) throws IOException {
        try {
            long offset = entry.offset;
            long remaining = entry.size;
            byte[] buffer = new byte[262144]; // 256KB buffer
            
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                byte[] data = IsoParser.readRange(offset, toRead);
                
                if (data.length == 0) {
                    System.err.println("No data received at offset " + offset);
                    break;
                }
                
                out.write(data);
                out.flush(); // 及时刷新
                offset += data.length;
                remaining -= data.length;
                
                if (remaining % (1024*1024) == 0) {
                    System.out.println("Sent " + (entry.size - remaining) + " / " + entry.size);
                }
            }
            
            System.out.println("Stream complete: " + entry.size + " bytes");
        } catch (Exception e) {
            System.err.println("Stream error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 流式传输指定范围
    private static void streamFileRange(IsoParser.FileEntry entry, long start, long length, OutputStream out) throws IOException {
        try {
            long offset = entry.offset + start;
            long remaining = length;
            byte[] buffer = new byte[65536];
            
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                byte[] data = IsoParser.readRange(offset, toRead);
                
                if (data.length == 0) break;
                
                out.write(data);
                offset += data.length;
                remaining -= data.length;
            }
            
            out.flush();
        } catch (Exception e) {
            System.err.println("Range stream error: " + e.getMessage());
        }
    }
}