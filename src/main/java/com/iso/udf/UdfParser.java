package com.iso.udf;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * UDF ISO 解析器 - 基于7z的UdfIn.cpp实现
 * 直接从网络URL读取ISO数据，支持HTTP Range请求
 */
public class UdfParser {
    // Descriptor types (ECMA 3/7.2)
    private static final int DESC_TYPE_PRIMARY_VOL = 1;
    private static final int DESC_TYPE_ANCHOR_VOL_PTR = 2;
    private static final int DESC_TYPE_VOL_PTR = 3;
    private static final int DESC_TYPE_PARTITION = 5;
    private static final int DESC_TYPE_LOGICAL_VOL = 6;
    private static final int DESC_TYPE_UNALLOC_SPACE = 7;
    private static final int DESC_TYPE_TERMINATING = 8;
    private static final int DESC_TYPE_LOGICAL_VOL_INTEGRITY = 9;
    private static final int DESC_TYPE_FILE_SET = 256;
    private static final int DESC_TYPE_FILE_ID = 257;
    private static final int DESC_TYPE_ALLOCATION_EXTENT = 258;
    private static final int DESC_TYPE_FILE = 261;
    private static final int ICB_FILE_TYPE_DIR = 1;
    private static final int ICB_FILE_TYPE_FILE = 2;
    
    private static final int DESC_TYPE_EXTENDED_FILE = 266;

    private static final int ANCHOR_VOL_DESCRIPTOR_LOCATION = 256;

    // Network connection
    private URL url;
    private final RandomAccessFile localFile;
    private boolean isNetwork;

    private int sectorSize = 2048;

    // Parsed structures
    private long partitionStart = 0;  // Byte offset in ISO
    private long partitionLength = 0;
    private int blockSize = 2048;
    private long fileSetLocation = 0; // Sector number

    // Partition info
    private long metadataPartitionStart = 0;
    private long metadataPartitionLength = 0;
    private int numPartitionMaps = 0;

    public UdfParser(String urlStr) throws IOException {
        this.url = new URL(urlStr);
        this.localFile = null;
        this.isNetwork = true;
    }
    
    public UdfParser(RandomAccessFile file) throws IOException {
        this.url = null;
        this.localFile = file;
        this.isNetwork = false;
    }

    /**
     * 解析ISO的UDF结构
     */
    public List<IsoFile> parse() throws IOException {
        System.out.println("Connecting to: " + url);
        
        long anchorOffset = findAnchorVolumeDescriptor();
        if (anchorOffset < 0) {
            throw new IOException("Cannot find Anchor Volume Descriptor");
        }

        parseVolumeDescriptors(anchorOffset);
        return parseRootDirectory();
    }

    /**
     * 从网络读取指定偏移的数据 - 自动处理302重定向
     */
    private byte[] readRange(long offset, int length) throws IOException {
        HttpURLConnection conn = null;
        int maxRedirects = 5;
        
        for (int redirect = 0; redirect < maxRedirects; redirect++) {
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 302 || responseCode == 301 || responseCode == 303 || responseCode == 307 || responseCode == 308) {
                // Handle redirect
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                if (newUrl != null) {
                    url = new URL(newUrl);
                    System.out.println("Redirect to: " + newUrl);
                    continue;
                }
            }
            
            if (responseCode != 200 && responseCode != 206) {
                conn.disconnect();
                throw new IOException("HTTP error: " + responseCode + " for range " + offset + "-" + (offset + length));
            }
            
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
                if (baos.size() >= length) break;
            }
            is.close();
            conn.disconnect();
            
            byte[] result = baos.toByteArray();
            if (result.length < length) {
                throw new IOException("Short read: got " + result.length + " expected " + length);
            }
            return result;
        }
        
        throw new IOException("Too many redirects");
    }

    private long findAnchorVolumeDescriptor() throws IOException {
        // Anchor VD at sector 256
        long offset = (long) ANCHOR_VOL_DESCRIPTOR_LOCATION * sectorSize;
        System.out.println("Reading Anchor VD at offset " + offset);
        
        byte[] buf = readRange(offset, sectorSize);
        
        if (isAnchorVolumeDescriptor(buf)) {
            System.out.println("Found Anchor VD at offset " + offset);
            return offset;
        }
        return -1;
    }

    private boolean isAnchorVolumeDescriptor(byte[] buf) {
        if (buf.length < 16) return false;
        int tagId = ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF);
        return tagId == DESC_TYPE_ANCHOR_VOL_PTR;
    }

    private void parseVolumeDescriptors(long anchorOffset) throws IOException {
        // Read Main Volume Descriptor Sequence location from Anchor VD
        // At offset 16: extent length (4 bytes) + extent location (4 bytes)
        byte[] anchorBuf = readRange(anchorOffset, sectorSize);
        
        long mainVdsLength = readUInt32(anchorBuf, 16);
        long mainVdsLocation = readUInt32(anchorBuf, 20);
        
        System.out.println("Main VDS: len=" + mainVdsLength + ", loc=" + mainVdsLocation);

        // Read VDS region
        long mainVdsOffset = mainVdsLocation * (long) sectorSize;
        parseVolumeDescriptorSet(mainVdsOffset, mainVdsLength);
    }

    private long readUInt32(byte[] buf, int off) {
        return ((buf[off + 3] & 0xFF) << 24) | ((buf[off + 2] & 0xFF) << 16) | 
               ((buf[off + 1] & 0xFF) << 8) | (buf[off] & 0xFF);
    }
    
    private long readUInt64(byte[] buf, int off) {
        long high = readUInt32(buf, off);
        long low = readUInt32(buf, off + 4);
        return (high << 32) | (low & 0xFFFFFFFFL);
    }

    private void parseVolumeDescriptorSet(long startOffset, long maxLength) throws IOException {
        System.out.println("Parsing VDS from offset " + startOffset);
        
        long offset = startOffset;
        long endOffset = startOffset + maxLength;
        
        // Read VDS in chunks
        int vdsSize = (int) Math.min(maxLength, 16L * sectorSize);
        byte[] vdsData = readRange(startOffset, vdsSize);
        
        for (int i = 0; i < vdsSize; i += sectorSize) {
            if (i + 16 > vdsData.length) break;
            
            int tagId = readUInt16(vdsData, i);
            
            if (tagId == DESC_TYPE_PRIMARY_VOL) {
                parsePrimaryVolume(vdsData, i);
            } else if (tagId == DESC_TYPE_PARTITION) {
                parsePartitionDescriptor(vdsData, i);
            } else if (tagId == DESC_TYPE_LOGICAL_VOL) {
                parseLogicalVolume(vdsData, i);
            } else if (tagId == 0 || tagId == DESC_TYPE_TERMINATING) {
                break;
            }
        }
    }

    private int readUInt16(byte[] buf, int off) {
        return ((buf[off + 1] & 0xFF) << 8) | (buf[off] & 0xFF);
    }

    private void parsePrimaryVolume(byte[] data, int offset) throws IOException {
        // Volume ID at offset 24 (32 bytes UTF-16BE)
        String volumeId = "";
        try {
            byte[] volId = new byte[32];
            System.arraycopy(data, offset + 24, volId, 0, 32);
            volumeId = new String(volId, "UTF-16BE").trim();
        } catch (Exception e) {}
        System.out.println("Primary Volume: " + volumeId);
    }

    private void parsePartitionDescriptor(byte[] data, int offset) throws IOException {
        // Partition Location at offset 188 (4 bytes each) - Pos at 188, Len at 192
        long partitionStartLsn = readUInt32(data, offset + 188);
        long partitionLen = readUInt32(data, offset + 192);
        
        // Check Partition Contents at offset 56
        String contents = "";
        try {
            byte[] cont = new byte[32];
            System.arraycopy(data, offset + 96, cont, 0, 32);
            contents = new String(cont, "ISO-8859-1").trim();
        } catch (Exception e) {}
        
        if (contents.contains("Metadata") || contents.contains("metadata")) {
            metadataPartitionStart = partitionStartLsn * sectorSize;
            metadataPartitionLength = partitionLen;
            System.out.println("Metadata Partition: startLsn=" + partitionStartLsn + ", len=" + partitionLen);
        } else {
            partitionStart = partitionStartLsn * sectorSize;
            partitionLength = partitionLen;
            System.out.println("Main Partition: startLsn=" + partitionStartLsn + ", len=" + partitionLen);
        }
    }

    private void parseLogicalVolume(byte[] data, int offset) throws IOException {
        // Block size at offset 212
        blockSize = (int) readUInt32(data, offset + 212);
        
        // File Set Location at offset 248 (LongAD: len + location)
        long fileSetLen = readUInt32(data, offset + 248);
        fileSetLocation = readUInt32(data, offset + 252);
        
        // Partition maps at offset 440+
        numPartitionMaps = (int) readUInt32(data, offset + 268);
        
        System.out.println("Logical Volume:");
        System.out.println("  BlockSize: " + blockSize);
        System.out.println("  FileSet Location: sector " + fileSetLocation + " (len=" + fileSetLen + ")");
        System.out.println("  Partition Maps: " + numPartitionMaps);
    }

    private List<IsoFile> parseRootDirectory() throws IOException {
        // FileSet is at partition start + fileSetLocation * sectorSize
        long fileSetOffset = partitionStart + fileSetLocation * sectorSize;
        System.out.println("Reading FileSet from sector " + fileSetLocation + " (offset " + fileSetOffset + ")");
        
        try {
            byte[] fsData = readRange(fileSetOffset, sectorSize);
            int tagId = readUInt16(fsData, 0);
            
            System.out.println("FileSet tagId: " + tagId);
            
            if (tagId == DESC_TYPE_FILE_SET) {
                // Root Directory ICB at offset 168
                long rootIcbLen = readUInt32(fsData, 168);
                long rootIcbPos = readUInt32(fsData, 172);
                
                System.out.println("Root ICB: pos=" + rootIcbPos + ", len=" + rootIcbLen);
                
                return readDirectory(rootIcbPos, rootIcbLen);
            } else if (tagId == DESC_TYPE_FILE || tagId == DESC_TYPE_EXTENDED_FILE) {
                // This is directly a file entry (not a FileSet descriptor)
                // The root directory ICB is embedded
                System.out.println("File entry directly at partition start, parsing...");
                return parseFileEntry(fsData, 0);
            }
        } catch (IOException e) {
            System.out.println("Note: Cannot read FileSet from network: " + e.getMessage());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 解析FileEntry获取文件信息
     */
    private List<IsoFile> parseFileEntry(byte[] data, int offset) throws IOException {
        List<IsoFile> files = new ArrayList<>();
        
        // Check if this is a directory
        int icbFileType = data[offset + 27] & 0xFF;
        boolean isDirectory = (icbFileType == ICB_FILE_TYPE_DIR);
        
        System.out.println("FileEntry type: " + icbFileType + " (directory=" + isDirectory + ")");
        
        // File size at offset 56
        long fileSize = readUInt64(data, offset + 96);
        System.out.println("File size: " + fileSize);
        
        // Allocation descriptor
        int extAttrLen = (int) readUInt32(data, offset + 208);
        int allocDescLen = (int) readUInt32(data, offset + 212);
        
        System.out.println("ExtAttrLen=" + extAttrLen + ", AllocDescLen=" + allocDescLen);
        
        // For directories, read the allocation descriptor to find data
        if (isDirectory && allocDescLen > 0) {
            int descStart = 216 + extAttrLen;
            
            if (data.length >= descStart + 16) {
                long dataOffset = readUInt32(data, descStart + 4);
                long dataLen = readUInt32(data, descStart);
                
                System.out.println("Directory data: offset=" + dataOffset + ", len=" + dataLen);
                
                // Read directory entries
                long dirDataOffset = dataOffset * (long) sectorSize + partitionStart;
                int readLen = (int) Math.min(dataLen, 65536);
                
                try {
                    byte[] dirEntries = readRange(dirDataOffset, readLen);
                    // Reuse the directory parsing logic
                    files.addAll(parseDirectoryEntries(dirEntries));
                } catch (IOException e) {
                    System.out.println("Could not read directory data: " + e.getMessage());
                }
            }
        }
        
        return files;
    }
    
    /**
     * Parse directory entries from directory data
     */
    private List<IsoFile> parseDirectoryEntries(byte[] dirEntries) throws IOException {
        List<IsoFile> files = new ArrayList<>();
        
        int pos = 0;
        while (pos + 38 < dirEntries.length) {
            int tid = readUInt16(dirEntries, pos);
            
            if (tid != DESC_TYPE_FILE_ID) {
                pos++;
                continue;
            }
            
            byte fileChar = dirEntries[pos + 18];
            if ((fileChar & 0x01) != 0 || (fileChar & 0x02) != 0 || (fileChar & 0x04) != 0) {
                pos += 38;
                continue;
            }
            
            int idLen = dirEntries[pos + 19];
            long icbFilePos = readUInt32(dirEntries, pos + 20);
            int impLen = readUInt16(dirEntries, pos + 36);
            
            int headerLen = 38 + impLen + idLen;
            while ((headerLen & 3) != 0) headerLen++;
            
            if (pos + headerLen > dirEntries.length) break;
            
            String fileName = "";
            try {
                fileName = new String(dirEntries, 38 + impLen, idLen, "UTF-16BE").replaceAll("\0", "");
            } catch (Exception e) {}
            
            if (!fileName.isEmpty() && !fileName.equals(".")) {
                IsoFile f = new IsoFile();
                f.setName(fileName);
                f.setStartSector(icbFilePos);
                files.add(f);
            }
            
            pos += headerLen;
        }
        
        return files;
    }

    private List<IsoFile> readDirectory(long icbPos, long icbLen) throws IOException {
        List<IsoFile> files = new ArrayList<>();
        
        long offset = icbPos * (long) sectorSize + partitionStart;
        byte[] dirData = readRange(offset, sectorSize);
        
        int tagId = readUInt16(dirData, 0);
        if (tagId != DESC_TYPE_FILE && tagId != DESC_TYPE_EXTENDED_FILE) {
            System.out.println("Not a file entry, tagId=" + tagId);
            return files;
        }
        
        // Read allocation descriptor
        int extAttrLen = (int) readUInt32(dirData, 168);
        int allocDescLen = (int) readUInt32(dirData, 172);
        
        int descStart = 216 + extAttrLen;
        
        if (allocDescLen > 0 && dirData.length >= descStart + 16) {
            long dataOffset = readUInt32(dirData, descStart + 4);
            long dataLen = readUInt32(dirData, descStart);
            
            long dirDataOffset = dataOffset * (long) sectorSize + partitionStart;
            int readLen = (int) Math.min(dataLen, 65536);
            
            byte[] dirEntries = readRange(dirDataOffset, readLen);
            
            int pos = 0;
            while (pos + 38 < dirEntries.length) {
                int tid = readUInt16(dirEntries, pos);
                
                if (tid != DESC_TYPE_FILE_ID) {
                    pos++;
                    continue;
                }
                
                byte fileChar = dirEntries[pos + 18];
                if ((fileChar & 0x01) != 0 || (fileChar & 0x02) != 0 || (fileChar & 0x04) != 0) {
                    pos += 38;
                    continue;
                }
                
                int idLen = dirEntries[pos + 19];
                long icbFilePos = readUInt32(dirEntries, pos + 20);
                
                int impLen = readUInt16(dirEntries, pos + 36);
                int headerLen = 38 + impLen + idLen;
                while ((headerLen & 3) != 0) headerLen++;
                
                if (pos + headerLen > dirEntries.length) break;
                
                String fileName = "";
                try {
                    fileName = new String(dirEntries, 38 + impLen, idLen, "UTF-16BE").replaceAll("\0", "");
                } catch (Exception e) {}
                
                if (!fileName.isEmpty() && !fileName.equals(".")) {
                    IsoFile f = new IsoFile();
                    f.setName(fileName);
                    f.setStartSector(icbFilePos);
                    files.add(f);
                }
                
                pos += headerLen;
            }
        }
        
        return files;
    }

    // === 网络流读取器 ===
    
    /**
     * 创建文件流读取器
     */
    public IsoFileReader createFileReader() {
        return new IsoFileReader();
    }
    
    /**
     * ISO文件读取器 - 支持从网络流式读取文件数据
     */
    public class IsoFileReader {
        /**
         * 读取文件数据（流式）
         * @param fileStartSector 文件起始扇区
         * @param fileSize 文件大小
         * @return InputStream
         */
        public InputStream openFile(long fileStartSector, long fileSize) throws IOException {
            long offset = fileStartSector * (long) sectorSize + partitionStart;
            return new IsoRangeInputStream(url, offset, fileSize);
        }
        
        /**
         * 读取指定扇区范围的数据
         */
        public byte[] readSectors(long startSector, int count) throws IOException {
            long offset = startSector * (long) sectorSize + partitionStart;
            int len = count * sectorSize;
            return UdfParser.this.readRange(offset, len);
        }
    }
    
    /**
     * ISO Range InputStream - 从网络流式读取
     */
    public static class IsoRangeInputStream extends InputStream {
        private URL url;
        private long offset;
        private final long length;
        private long position = 0;
        private InputStream currentStream;
        private HttpURLConnection conn;
        private static final int CHUNK_SIZE = 65536;
        
        public IsoRangeInputStream(URL url, long offset, long length) {
            this.url = url;
            this.offset = offset;
            this.length = length;
        }
        
        private void openChunk() throws IOException {
            if (currentStream != null && currentStream.available() > 0) {
                return;
            }
            
            if (conn != null) {
                conn.disconnect();
            }
            
            long chunkEnd = Math.min(offset + CHUNK_SIZE, offset + length - position);
            if (chunkEnd <= offset) {
                currentStream = null;
                return;
            }
            
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (chunkEnd - 1));
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 206) {
                throw new IOException("HTTP error: " + responseCode);
            }
            
            currentStream = conn.getInputStream();
        }
        
        @Override
        public int read() throws IOException {
            if (position >= length) return -1;
            openChunk();
            if (currentStream == null) return -1;
            
            int b = currentStream.read();
            if (b >= 0) {
                position++;
                offset++;
            }
            return b;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (position >= length) return -1;
            openChunk();
            if (currentStream == null) return -1;
            
            int toRead = (int) Math.min(len, length - position);
            int read = currentStream.read(b, off, toRead);
            if (read > 0) {
                position += read;
                offset += read;
            }
            return read;
        }
        
        @Override
        public long skip(long n) throws IOException {
            if (position >= length) return -1;
            long skipped = Math.min(n, length - position);
            position += skipped;
            offset += skipped;
            return skipped;
        }
        
        @Override
        public int available() throws IOException {
            if (currentStream != null) {
                return (int) Math.min(currentStream.available(), length - position);
            }
            return 0;
        }
        
        @Override
        public void close() throws IOException {
            if (currentStream != null) {
                currentStream.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Getters
    public long getPartitionStart() { return partitionStart; }
    public long getPartitionLength() { return partitionLength; }
    public long getPartitionStartLsn() { return partitionStart / sectorSize; }
    public int getBlockSize() { return blockSize; }
    public int getSectorSize() { return sectorSize; }
    public long getMetadataPartitionStart() { return metadataPartitionStart; }
    public long getMetadataPartitionLength() { return metadataPartitionLength; }
    public long getFileSetLocation() { return fileSetLocation; }
    public int getNumPartitionMaps() { return numPartitionMaps; }
    
    /**
     * 计算文件在ISO中的物理偏移
     */
    public long getFileOffset(long fileStartSector) {
        return fileStartSector * sectorSize + partitionStart;
    }
}