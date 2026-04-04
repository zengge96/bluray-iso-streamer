package com.iso;

import com.iso.udf.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * HTTP Service for ISO streaming
 * Endpoints:
 *   / - File list (HTML)
 *   /stream?file=<name>&offset=<sector>&size=<bytes> - Stream file data
 *   /info - ISO info (partition, sector size, etc.)
 */
public class HttpService {
    private static final int PORT = 8080;
    private static UdfParser parser;
    private static String isoUrl;
    
    public static void main(String[] args) throws Exception {
        // Read URL from file or args
        if (args.length > 0) {
            isoUrl = args[0];
        } else {
            isoUrl = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        }
        
        System.out.println("Starting ISO HTTP Service...");
        System.out.println("ISO URL: " + isoUrl);
        
        // Parse ISO to get structure
        System.out.println("\nParsing ISO structure...");
        parser = new UdfParser(isoUrl);
        parser.parse();
        
        System.out.println("\nISO Info:");
        System.out.println("  Sector Size: " + parser.getSectorSize());
        System.out.println("  Partition Start: sector " + parser.getPartitionStartLsn());
        System.out.println("  Partition Size: " + parser.getPartitionLength() + " bytes");
        
        // Start HTTP server
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("\nServer running at http://localhost:" + PORT);
        System.out.println("Endpoints:");
        System.out.println("  /           - File list (HTML)");
        System.out.println("  /info       - ISO info (JSON)");
        System.out.println("  /stream     - Stream data (needs offset & size params)");
        
        while (true) {
            Socket client = server.accept();
            new Thread(() -> handleRequest(client)).start();
        }
    }
    
    private static void handleRequest(Socket client) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String requestLine = in.readLine();
            
            if (requestLine == null) {
                client.close();
                return;
            }
            
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts.length > 1 ? parts[1] : "/";
            
            // Parse query params
            String query = "";
            if (path.contains("?")) {
                String[] pathParts = path.split("\\?", 2);
                path = pathParts[0];
                query = pathParts[1];
            }
            
            String response;
            String contentType = "text/html; charset=utf-8";
            
            if (path.equals("/")) {
                response = getFileListHtml();
            } else if (path.equals("/info")) {
                response = getInfoJson();
                contentType = "application/json";
            } else if (path.equals("/stream")) {
                // Stream data
                Map<String, String> params = parseQuery(query);
                String offsetStr = params.get("offset");
                String sizeStr = params.get("size");
                
                if (offsetStr == null || sizeStr == null) {
                    response = "{\"error\": \"missing offset or size parameter\"}";
                    contentType = "application/json";
                } else {
                    long offset = Long.parseLong(offsetStr);
                    long size = Long.parseLong(sizeStr);
                    streamData(client, offset, size);
                    return;
                }
            } else {
                response = "<html><body><h1>404 Not Found</h1></body></html>";
            }
            
            // Send response
            PrintWriter out = new PrintWriter(client.getOutputStream());
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + response.length());
            out.println("Connection: close");
            out.println();
            out.print(response);
            out.flush();
            client.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            try { client.close(); } catch (Exception ex) {}
        }
    }
    
    private static String getFileListHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>ISO Streaming Service</title>");
        sb.append("<style>body{font-family:Arial;margin:20px;max-width:800px}");
        sb.append("table{border-collapse:collapse;width:100%;margin:10px 0}");
        sb.append("th,td{border:1px solid #ddd;padding:8px;text-align:left}");
        sb.append("th{background-color:#4CAF50;color:white}");
        sb.append(".info-box{background:#e7f3fe;border-left:4px solid #2196F3;padding:10px;margin:10px 0}");
        sb.append(".warning{background:#fff3cd;border-left:4px solid #ffc107;padding:10px;margin:10px 0}");
        sb.append("pre{background:#f5f5f5;padding:10px;border-radius:4px;overflow-x:auto}");
        sb.append("</style></head><body>");
        sb.append("<h1>🎬 ISO Streaming Service</h1>");
        
        // ISO Info
        sb.append("<div class='info-box'>");
        sb.append("<h2>📀 ISO Information</h2>");
        sb.append("<table>");
        sb.append("<tr><th>Property</th><th>Value</th></tr>");
        sb.append("<tr><td>Sector Size</td><td>").append(parser.getSectorSize()).append(" bytes</td></tr>");
        sb.append("<tr><td>Partition Start</td><td>sector ").append(parser.getPartitionStartLsn()).append("</td></tr>");
        sb.append("<tr><td>Partition Size</td><td>").append(String.format("%.2f GB", parser.getPartitionLength()/1024.0/1024.0/1024.0)).append("</td></tr>");
        sb.append("<tr><td>Block Size</td><td>").append(parser.getBlockSize()).append(" bytes</td></tr>");
        sb.append("</table></div>");
        
        // File list note
        sb.append("<div class='warning'>");
        sb.append("<h2>📁 File List</h2>");
        sb.append("<p><b>Note:</b> For Blu-ray ISOs, the file list (FileSet) is located in the main partition.</p>");
        sb.append("<p>This ISO's main partition starts at <b>sector ").append(parser.getPartitionStartLsn());
        sb.append("</b> (approximately 1.2GB into the file).</p>");
        sb.append("<p>To list files, you would need to download the complete ISO or the metadata partition.</p>");
        sb.append("</div>");
        
        // Streaming API
        sb.append("<h2>📡 Streaming API</h2>");
        sb.append("<p>You can manually stream data by specifying sector offset and size:</p>");
        sb.append("<pre>/stream?offset=&lt;sector_number&gt;&size=&lt;bytes&gt;</pre>");
        sb.append("<h3>Examples:</h3>");
        sb.append("<ul>");
        sb.append("<li>Read partition start: <code>/stream?offset=").append(parser.getPartitionStartLsn()).append("&size=4096</code></li>");
        sb.append("<li>Read 64KB from sector 0: <code>/stream?offset=0&size=65536</code></li>");
        sb.append("</ul>");
        
        sb.append("<h2>🔧 Quick Reference</h2>");
        sb.append("<table>");
        sb.append("<tr><th>Location</th><th>Sector</th><th>Description</th></tr>");
        sb.append("<tr><td>Anchor VD</td><td>256</td><td>Volume descriptors anchor</td></tr>");
        sb.append("<tr><td>VDS</td><td>32-47</td><td>Volume Descriptor Sequence</td></tr>");
        sb.append("<tr><td>Main Partition</td><td>").append(parser.getPartitionStartLsn()).append("</td><td>BDAREA file content</td></tr>");
        sb.append("</table>");
        
        sb.append("</body></html>");
        return sb.toString();
    }
    
    private static String getInfoJson() {
        return String.format(
            "{\"sectorSize\":%d,\"partitionStart\":%d,\"partitionSize\":%d,\"blockSize\":%d}",
            parser.getSectorSize(),
            parser.getPartitionStartLsn(),
            parser.getPartitionLength(),
            parser.getBlockSize()
        );
    }
    
    private static void streamData(Socket client, long sector, long size) throws Exception {
        long offset = sector * parser.getSectorSize() + parser.getPartitionStart();
        
        System.out.println("Streaming: sector " + sector + " (offset " + offset + "), size " + size);
        
        // Send HTTP headers
        PrintWriter out = new PrintWriter(client.getOutputStream());
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: application/octet-stream");
        out.println("Content-Length: " + size);
        out.println("Content-Disposition: attachment; filename=\"stream_" + sector + ".bin\"");
        out.println("Accept-Ranges: bytes");
        out.println();
        out.flush();
        
        // Stream data
        OutputStream os = client.getOutputStream();
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // Use readSectors - offset param is sector number
        long remaining = size;
        long currentSector = sector;
        
        while (remaining > 0) {
            int chunkSectors = (int) Math.min(32, remaining / parser.getSectorSize());
            if (chunkSectors == 0) chunkSectors = 1;
            
            byte[] data = reader.readSectors(currentSector, chunkSectors);
            
            if (data.length == 0) break;
            
            os.write(data);
            remaining -= data.length;
            currentSector += chunkSectors;
        }
        
        os.flush();
        client.close();
        System.out.println("Stream complete: " + (size - remaining) + " bytes");
    }
    
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }
}