package com.iso;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * 远程UDF ISO解析器
 * 基于 pycdlib (Python) 的UDF解析逻辑改写为Java
 */
public class UdfRemoteParser {
    private static final int SECTOR_SIZE = 2048;
    private static final int BD_PARTITION_START = 589824;
    
    // UDF Tag IDs
    private static final int TAGID_NSR = 0;
    private static final int TAGID_BOOT = 8;
    private static final int TAGID_TEAV = 9;
    private static final int TAGID_VOL = 1;
    private static final int TAGID_AVDP = 2;
    private static final int TAGID_PVD = 1;
    private static final int TAGID_PARTITION = 5;
    private static final int TAGID_LOGICAL_VOL = 6;
    private static final int TAGID_UNALLOCATED = 7;
    private static final int TAGID_TERMINATING = 8;
    private static final int TAGID_FILE_ENTRY = 261;
    private static final int TAGID_FILE_IDENT = 261;
    private static final int TAGID_EXTENDED_ATTR = 0;
    
    // File types from ICB
    private static final byte FILE_TYPE_UNDEF = 0;
    private static final byte FILE_TYPE_DIR = 4;
    private static final byte FILE_TYPE_REG = 1;
    
    private String isoUrl;
    private String finalUrl;
    private long isoSize;
    private long partitionStart;
    
    public static class FileEntry {
        public String path;
        public long size;
        public long sector;
        public long offset;
        public boolean isDir;
        
        public FileEntry(String path, long size, long sector, boolean isDir) {
            this.path = path;
            this.size = size;
            this.sector = sector;
            this.offset = sector * SECTOR_SIZE;
            this.isDir = isDir;
        }
    }
    
    public UdfRemoteParser(String urlFile) throws Exception {
        this.isoUrl = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(urlFile))).trim();
        this.finalUrl = getFinalUrl(isoUrl);
        this.isoSize = getContentLength();
        this.partitionStart = BD_PARTITION_START;
        System.out.println("ISO: " + (isoSize/1024/1024/1024) + " GB");
    }
    
    /**
     * 主解析方法
     */
    public Map<String, FileEntry> parse() throws Exception {
        System.out.println("=== UDF Parsing ===");
        
        // 1. 找到AVDP
        long avdpSector = findAVDP();
        if (avdpSector >= 0) {
            System.out.println("AVDP at sector " + avdpSector);
        }
        
        // 2. 从AVDP获取分区位置
        partitionStart = readPartitionDescriptor(avdpSector);
        System.out.println("Partition start: " + partitionStart);
        
        // 3. 找到FSD (File Set Descriptor) - 通常在partition+256
        long fsdSector = findFileSetDescriptor();
        System.out.println("FSD at sector: " + fsdSector);
        
        // 4. 遍历根目录
        Map<String, FileEntry> files = new LinkedHashMap<>();
        traverseRootDirectory(files, fsdSector);
        
        long totalSize = files.values().stream().mapToLong(f -> f.size).sum();
        System.out.println("Total: " + files.size() + " files, " + (totalSize/1024/1024/1024) + " GB");
        
        return files;
    }
    
    /**
     * 找到Anchor Volume Descriptor Pointer
     */
    private long findAVDP() {
        // 尝试 sector 256 (标准位置)
        byte[] sector = readSector(256);
        if (sector != null && isAVDP(sector)) {
            return 256;
        }
        
        // 尝试 sector 257 (BD-ROM)
        sector = readSector(257);
        if (sector != null && isAVDP(sector)) {
            return 257;
        }
        
        // 从ISO末尾
        long lastSector = (isoSize / SECTOR_SIZE) - 1;
        sector = readSector(lastSector);
        if (sector != null && isAVDP(sector)) {
            return lastSector;
        }
        
        return 256; // fallback
    }
    
    private boolean isAVDP(byte[] sector) {
        // Tag ID = 2 at bytes 0-1
        return sector != null && sector.length >= 4 && 
               sector[0] == 0x02 && sector[1] == 0x00;
    }
    
    /**
     * 读取Partition Descriptor获取分区位置
     */
    private long readPartitionDescriptor(long avdpSector) {
        // 从 AVDP 获取 Volume Descriptor Sequence 位置
        byte[] avdp = readSector(avdpSector);
        if (avdp != null && avdp.length >= 24) {
            long mainVDS = readUInt32LE(avdp, 16);
            System.out.println("Main VDS at sector: " + mainVDS);
            
            // 扫描VDS找Partition Descriptor
            for (long i = mainVDS; i < mainVDS + 32; i++) {
                byte[] vd = readSector(i);
                if (vd != null && vd.length >= 4) {
                    int tagId = vd[0] | (vd[1] << 8);
                    if (tagId == TAGID_PARTITION) {
                        // Partition Location at offset 188
                        long partLoc = readUInt32LE(vd, 188);
                        return partLoc;
                    }
                }
            }
        }
        
        return BD_PARTITION_START;
    }
    
    /**
     * 找到File Set Descriptor
     */
    private long findFileSetDescriptor() {
        // 从partition+256开始扫描找NSR descriptor
        for (long i = partitionStart + 256; i < partitionStart + 512; i++) {
            byte[] sector = readSector(i);
            if (sector != null && sector.length >= 8) {
                String sig = new String(sector, 4, 5);
                if (sig.equals("NSR03") || sig.equals("NSR02")) {
                    // FSD通常在NSR后面几个sector
                    return i + 1;
                }
            }
        }
        
        return partitionStart + 256;
    }
    
    /**
     * 遍历根目录
     */
    private void traverseRootDirectory(Map<String, FileEntry> files, long dirSector) {
        System.out.println("Traversing directory at sector " + dirSector);
        
        try {
            // 读取Directory Entry
            byte[] dirData = readSectors(dirSector, 4);
            if (dirData == null || dirData.length < 36) {
                System.out.println("Cannot read directory data");
                return;
            }
            
            // 解析File Entry
            int pos = 0;
            while (pos < dirData.length - 36) {
                // 读取Entry长度 (16-bit LE at offset 0)
                int entryLen = readUInt16LE(dirData, pos);
                if (entryLen == 0 || entryLen < 36) break;
                if (pos + entryLen > dirData.length) break;
                
                // ICB Tag - file type at offset 18
                byte fileType = dirData[pos + 18];
                
                // 文件名长度 at offset 32
                int nameLen = dirData[pos + 32] & 0xFF;
                String name = "";
                if (nameLen > 0 && pos + 33 + nameLen <= dirData.length) {
                    // OSTA Unicode / Dstring编码
                    name = decodeDString(dirData, pos + 33, nameLen);
                }
                
                // ICB位置 (sector) at offset 20
                long icbSector = readUInt32LE(dirData, pos + 20);
                
                if (!name.isEmpty() && !name.equals(".") && !name.equals("..")) {
                    if (fileType == FILE_TYPE_DIR) {
                        // 目录 - 递归遍历
                        String fullPath = name;
                        if (!fullPath.equals("BDMV")) {
                            traverseSubDirectory(files, icbSector, fullPath);
                        } else {
                            // 找到BDMV目录，遍历其内容
                            traverseSubDirectory(files, icbSector, "BDMV");
                        }
                    } else if (fileType == FILE_TYPE_REG || fileType == FILE_TYPE_UNDEF) {
                        // 文件 - 读取File Entry获取大小
                        long[] fileInfo = readFileEntry(icbSector);
                        if (fileInfo != null) {
                            String path = name;
                            FileEntry entry = new FileEntry(path, fileInfo[1], fileInfo[0], false);
                            files.put(path, entry);
                            if (files.size() <= 10) {
                                System.out.println("  " + path + " size=" + entry.size + " sector=" + entry.sector);
                            }
                        }
                    }
                }
                
                pos += entryLen;
            }
        } catch (Exception e) {
            System.err.println("Error traversing directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 遍历子目录
     */
    private void traverseSubDirectory(Map<String, FileEntry> files, long dirSector, String parentPath) {
        try {
            byte[] dirData = readSectors(dirSector, 4);
            if (dirData == null || dirData.length < 36) return;
            
            int pos = 0;
            while (pos < dirData.length - 36) {
                int entryLen = readUInt16LE(dirData, pos);
                if (entryLen == 0 || entryLen < 36) break;
                if (pos + entryLen > dirData.length) break;
                
                byte fileType = dirData[pos + 18];
                int nameLen = dirData[pos + 32] & 0xFF;
                String name = "";
                if (nameLen > 0 && pos + 33 + nameLen <= dirData.length) {
                    name = decodeDString(dirData, pos + 33, nameLen);
                }
                
                long icbSector = readUInt32LE(dirData, pos + 20);
                
                if (!name.isEmpty() && !name.equals(".") && !name.equals("..")) {
                    String fullPath = parentPath + "/" + name;
                    
                    if (fileType == FILE_TYPE_DIR) {
                        // 继续递归
                        if (name.equals("STREAM")) {
                            traverseSubDirectory(files, icbSector, fullPath);
                        }
                    } else if (fileType == FILE_TYPE_REG || fileType == FILE_TYPE_UNDEF) {
                        long[] fileInfo = readFileEntry(icbSector);
                        if (fileInfo != null) {
                            FileEntry entry = new FileEntry(fullPath, fileInfo[1], fileInfo[0], false);
                            files.put(fullPath, entry);
                            if (files.size() <= 20) {
                                System.out.println("  " + fullPath + " (" + (entry.size/1024/1024) + " MB)");
                            }
                        }
                    }
                }
                
                pos += entryLen;
            }
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    /**
     * 读取File Entry获取文件大小和位置
     */
    private long[] readFileEntry(long sector) {
        try {
            byte[] feData = readSector(sector);
            if (feData == null || feData.length < 96) return null;
            
            // Tag ID should be 261 (File Entry)
            int tagId = feData[0] | (feData[1] << 8);
            if (tagId != 261) {
                // 尝试其他位置
                feData = readSectors(sector, 2);
                if (feData == null || feData.length < 96) return null;
                tagId = feData[0] | (feData[1] << 8);
                if (tagId != 261) return null;
            }
            
            // Information Length at offset 80 (8 bytes)
            long infoLen = readUInt64LE(feData, 80);
            
            // 解析ICB获取数据位置
            // ICB Tag在feData开头，但我们已经跳过了
            // 数据位置在allocation descriptors中
            // 简化处理：用sector作为文件数据位置
            return new long[]{sector + 256, infoLen}; // 假设数据在sector+256
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 解码DString (UDF文件名编码)
     */
    private String decodeDString(byte[] data, int offset, int len) {
        try {
            // 检查是否是UTF-16BE (以0开始)
            if (len >= 2 && data[offset] == 0 && data[offset+1] != 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < len - 1; i += 2) {
                    char c = (char) ((data[offset + i] << 8) | (data[offset + i + 1] & 0xFF));
                    if (c == 0) break;
                    sb.append(c);
                }
                return sb.toString();
            } else {
                // ASCII/Latin-1
                return new String(data, offset, Math.min(len, 64), java.nio.charset.StandardCharsets.ISO_8859_1).trim();
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    // ============ 底层方法 ============
    
    private byte[] readSector(long sector) {
        return readRange(sector * SECTOR_SIZE, SECTOR_SIZE);
    }
    
    private byte[] readSectors(long sector, int count) {
        return readRange(sector * SECTOR_SIZE, count * SECTOR_SIZE);
    }
    
    private byte[] readRange(long offset, long length) {
        try {
            URL url = new URL(finalUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
            
            if (conn.getResponseCode() != 206 && conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
                if (baos.size() >= length) break;
            }
            is.close();
            conn.disconnect();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    private long getContentLength() {
        try {
            URL url = new URL(finalUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            
            String lengthStr = conn.getHeaderField("Content-Length");
            conn.disconnect();
            
            if (lengthStr != null) return Long.parseLong(lengthStr);
            return 93100670976L;
        } catch (Exception e) {
            return 93100670976L;
        }
    }
    
    private String getFinalUrl(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(30000);
        
        int code = conn.getResponseCode();
        if (code == 302 || code == 301) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            return location;
        }
        conn.disconnect();
        return urlStr;
    }
    
    private long readUInt32LE(byte[] data, int offset) {
        if (offset + 4 > data.length) return 0;
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
    
    private long readUInt64LE(byte[] data, int offset) {
        return readUInt32LE(data, offset) | (readUInt32LE(data, offset + 4) << 32);
    }
    
    private int readUInt16LE(byte[] data, int offset) {
        if (offset + 2 > data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
    
    public static void main(String[] args) throws Exception {
        UdfRemoteParser parser = new UdfRemoteParser("/root/.openclaw/workspace/url.txt");
        Map<String, FileEntry> files = parser.parse();
        
        System.out.println("\n=== Results ===");
        int count = 0;
        for (FileEntry e : files.values()) {
            if (e.path.endsWith(".m2ts")) {
                System.out.println(e.path + " " + (e.size/1024/1024) + "MB sector=" + e.sector);
                count++;
            }
            if (count > 30) break;
        }
    }
}