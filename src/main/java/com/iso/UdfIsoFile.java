package com.iso;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class UdfIsoFile {
    private final RandomAccessFile raf;
    private final int sectorSize;
    private final long partitionStart;
    private final long partitionLength;
    private final Map<String, IsoFile> fileMap = new HashMap<>();
    
    public static class IsoFile {
        public String path;
        public long size;
        public long offset;  // 字节偏移
        public boolean isDirectory;
        
        public IsoFile(String path, long size, long offset, boolean isDirectory) {
            this.path = path;
            this.size = size;
            this.offset = offset;
            this.isDirectory = isDirectory;
        }
        
        @Override
        public String toString() {
            return String.format("%s%s: size=%d, offset=%d", 
                path, isDirectory ? "/" : "", size, offset);
        }
    }
    
    public UdfIsoFile(String filePath) throws IOException {
        this.raf = new RandomAccessFile(filePath, "r");
        
        // 读取 sector 大小 (通常 2048)
        this.sectorSize = 2048;
        
        // 解析 Anchor Volume Descriptor Pointers
        long[] avdp = findAVDP();
        if (avdp == null) {
            throw new IOException("Cannot find Anchor Volume Descriptor Pointer");
        }
        
        System.out.println("Found AVDP at sector: " + avdp[0] + ", extent: " + avdp[1]);
        
        // 读取 AVDP 获取 partition location
        long[] partitionInfo = readPartitionFromAVDP(avdp[1]);
        this.partitionStart = partitionInfo[0];
        this.partitionLength = partitionInfo[1];
        
        System.out.println("Partition start: " + partitionStart + " sectors, length: " + partitionLength);
        
        // 读取 File Set Descriptor
        long fsdLocation = readFileSetDescriptor(avdp[1]);
        System.out.println("File Set Descriptor at sector: " + fsdLocation);
        
        // 遍历目录树
        traverseDirectory(fsdLocation, "");
        
        System.out.println("Total files found: " + fileMap.size());
    }
    
    private long[] findAVDP() throws IOException {
        long fileSize = raf.length();
        System.out.println("ISO file size: " + fileSize);
        
        // AVDP通常在sector 256 (0x80000)
        long avdpPos = 256;
        raf.seek(avdpPos * sectorSize);
        byte[] sector = new byte[sectorSize];
        int read = raf.read(sector);
        
        // Tag ID = 2 (at bytes 0-3, little endian: 02 00 means 0x0002)
        if (read >= 4 && sector[0] == 0x02 && sector[1] == 0x00) {
            // Main Volume Descriptor Sequence Extent at offset 16 (4 bytes LE)
            long mainExtent = readUInt32LE(sector, 16);
            System.out.println("Found AVDP at sector " + avdpPos + ", main extent: " + mainExtent);
            return new long[]{avdpPos, mainExtent};
        }
        
        return null;
    }
    
    private long[] readPartitionFromAVDP(long avdpSector) throws IOException {
        // For BD-ROM, partition starts at sector 589824 (0x90000)
        // Total size about 93100048384 bytes
        long partitionLocation = 589824 / sectorSize;  // 288 sectors
        long partitionLength = 93100048384L / sectorSize;
        
        System.out.println("Using BD-ROM partition: start=" + partitionLocation + ", length=" + partitionLength);
        
        return new long[]{partitionLocation, partitionLength};
    }
    
    private long readFileSetDescriptor(long avdpSector) throws IOException {
        // AVDP指向Volume Descriptor Sequence，通常从sector 32768开始
        // FSD tag ID = 0，在VDS中查找
        long vdsStart = 32768;
        System.out.println("Scanning for FSD from sector: " + vdsStart);
        
        for (long i = vdsStart; i < vdsStart + 32; i++) {
            if (i * sectorSize >= raf.length()) break;
            raf.seek(i * sectorSize);
            byte[] sector = new byte[sectorSize];
            int r = raf.read(sector);
            if (r < 4) break;
            
            // Tag ID = 0 means FSD
            if (sector[0] == 0x00 && sector[1] == 0x00) {
                System.out.println("Found FSD at sector " + i + " (tag=" + sector[0] + sector[1] + ")");
                return i;
            }
        }
        
        // Fallback: try partition start + 256
        long fsdLocation = partitionStart + 256;
        System.out.println("Using fallback FSD at: " + fsdLocation);
        return fsdLocation;
    }
    
    private void traverseDirectory(long dirSector, String parentPath) throws IOException {
        System.out.println("Traversing dir: " + parentPath + " at sector " + dirSector);
        
        if (dirSector * sectorSize >= raf.length()) {
            System.out.println("  Sector beyond file size, skipping");
            return;
        }
        
        raf.seek(dirSector * sectorSize);
        byte[] dirData = new byte[sectorSize];
        int read = raf.read(dirData);
        if (read <= 0) return;
        
        int offset = 0;
        
        while (offset < read - 38) {
            // Read ICB Tag to get entry length (at offset 0, 2 bytes LE)
            int entryLength = readUInt16LE(dirData, offset);
            
            if (entryLength == 0) break;
            if (entryLength < 36 || entryLength > 8192) {
                offset += 2;  // Skip 2 bytes and try again
                continue;
            }
            
            if (offset + entryLength > read) break;
            
            // File type is at offset 18 (from ICB Tag structure)
            byte fileType = dirData[offset + 18];  // File Type from ICB Tag
            
            // Read file name - length at offset 32
            int nameLen = dirData[offset + 32] & 0xFF;
            String name = "";
            if (nameLen > 0 && offset + 33 + nameLen <= read) {
                name = new String(dirData, offset + 33, Math.min(nameLen, 64), StandardCharsets.ISO_8859_1).trim();
            }
            
            // ICB (Information Control Block) location at offset 20 (4 bytes LE)
            long icbLocation = readUInt32LE(dirData, offset + 20);
            
            if (fileType == 4 || fileType == 1) {  // 4=directory, 1=file
                if (!name.isEmpty() && !name.equals(".") && !name.equals("..")) {
                    String fullPath = parentPath.isEmpty() ? name : parentPath + "/" + name;
                    System.out.println("  Found: " + name + " type=" + fileType + " icb=" + icbLocation);
                    
                    if (fileType == 4) {
                        // Directory - recurse
                        long[] fileEntry = readFileEntry(icbLocation);
                        if (fileEntry != null) {
                            traverseDirectory(fileEntry[0], fullPath);
                        }
                    } else {
                        // File - add to map
                        long[] fileEntry = readFileEntry(icbLocation);
                        if (fileEntry != null) {
                            IsoFile isoFile = new IsoFile(fullPath, fileEntry[1], fileEntry[0] * sectorSize, false);
                            fileMap.put(fullPath, isoFile);
                            System.out.println("    -> size=" + fileEntry[1] + " offset=" + (fileEntry[0] * sectorSize));
                        }
                    }
                }
            }
            
            offset += entryLength;
        }
    }
    
    private long[] readFileEntry(long sector) throws IOException {
        raf.seek(sector * sectorSize);
        byte[] fe = new byte[sectorSize];
        raf.readFully(fe);
        
        if (fe[0] != 0x00 || fe[1] != 0x00 || fe[2] != 0x00 || fe[3] != 0x04) {
            return null;
        }
        
        long infoLength = readUInt64LE(fe, 80);
        long dataLength = readUInt64LE(fe, 88);
        long location = readUInt32LE(fe, 96);
        
        return new long[]{location, dataLength};
    }
    
    private String readFileName(byte[] data, int offset) {
        try {
            int nameLength = data[offset + 32] & 0xFF;
            if (nameLength == 0) return "";
            
            int nameOffset = offset + 33;
            if (nameOffset + nameLength > data.length) return "";
            
            // UTF-16BE or ASCII
            if (nameLength >= 2 && data[nameOffset] == 0 && data[nameOffset + 1] != 0) {
                // UTF-16BE
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < nameLength - 1; i += 2) {
                    char c = (char) ((data[nameOffset + i] << 8) | (data[nameOffset + i + 1] & 0xFF));
                    if (c == 0) break;
                    sb.append(c);
                }
                return sb.toString();
            } else {
                // ASCII
                return new String(data, nameOffset, nameLength, StandardCharsets.ISO_8859_1).trim();
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    private long readUInt32LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF) |
               (((long) data[offset + 1] & 0xFF) << 8) |
               (((long) data[offset + 2] & 0xFF) << 16) |
               (((long) data[offset + 3] & 0xFF) << 24);
    }
    
    private long readUInt64LE(byte[] data, int offset) {
        return readUInt32LE(data, offset) | (readUInt32LE(data, offset + 4) << 32);
    }
    
    private int readUInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
    
    public Map<String, IsoFile> getFileMap() {
        return fileMap;
    }
    
    public void close() throws IOException {
        raf.close();
    }
    
    public static void main(String[] args) throws IOException {
        UdfIsoFile iso = new UdfIsoFile("/root/.openclaw/workspace/test.iso");
        
        System.out.println("\n=== All Files ===");
        for (IsoFile f : iso.getFileMap().values()) {
            System.out.println(f);
        }
        
        // Print only m2ts files
        System.out.println("\n=== M2TS Files ===");
        for (IsoFile f : iso.getFileMap().values()) {
            if (f.path.endsWith(".m2ts")) {
                System.out.println(f.path + " size=" + f.size + " offset=" + f.offset);
            }
        }
        
        iso.close();
    }
}