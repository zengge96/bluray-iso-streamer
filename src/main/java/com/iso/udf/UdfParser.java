package com.iso.udf;

import java.io.*;
import java.net.*;
import java.util.*;

public class UdfParser {
    private static final int DESC_TYPE_ANCHOR_VOL_PTR = 2;
    private static final int DESC_TYPE_PARTITION = 5;
    private static final int DESC_TYPE_LOGICAL_VOL = 6;
    private static final int DESC_TYPE_FILE_ID = 257;
    private static final int DESC_TYPE_EXTENDED_FILE = 266;
    private static final int ANCHOR_VOL_DESCRIPTOR_LOCATION = 256;
    
    private URL url;
    private URL actualUrl;
    private final RandomAccessFile localFile;
    private boolean isNetwork;
    
    private long partitionStart = 0;
    private long partitionLength = 0;
    
    private List<IsoFile> files = new ArrayList<>();
    
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
    
    public void parse() throws IOException {
        long anchorOffset = findAnchorVolumeDescriptor();
        if (anchorOffset < 0) throw new IOException("Cannot find Anchor VD");
        parseVolumeDescriptors(anchorOffset);
        parseMetadataPartition();
    }
    
    private long findAnchorVolumeDescriptor() throws IOException {
        long offset = (long) ANCHOR_VOL_DESCRIPTOR_LOCATION * 2048;
        byte[] buf = readRange(offset, 2048);
        int tagId = ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF);
        if (tagId == DESC_TYPE_ANCHOR_VOL_PTR) {
            return offset;
        }
        return -1;
    }
    
    private void parseVolumeDescriptors(long anchorOffset) throws IOException {
        byte[] vds = readRange(32L * 2048, 32768);
        for (int i = 0; i < 32768; i += 2048) {
            if (i + 32 > vds.length) break;
            int tag = ((vds[i+1]&0xFF)<<8)|(vds[i]&0xFF);
            if (tag == DESC_TYPE_PARTITION) {
                partitionStart = ((vds[i+191]&0xFF)<<24)|((vds[i+190]&0xFF)<<16)|
                                ((vds[i+189]&0xFF)<<8)|(vds[i+188]&0xFF);
                partitionLength = ((vds[i+195]&0xFF)<<24)|((vds[i+194]&0xFF)<<16)|
                                 ((vds[i+193]&0xFF)<<8)|(vds[i+192]&0xFF);
            }
        }
    }
    
    private void parseMetadataPartition() {
        System.out.println("Parsing metadata at sector 320...");
        try {
            for (int sec = 320; sec < 500; sec++) {
                byte[] data = readSectors(sec, 4);
                if (data == null || data.length < 38) continue;
                
                for (int offset = 0; offset < data.length - 38; offset += 4) {
                    int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
                    if (tag != DESC_TYPE_FILE_ID) continue;
                    
                    int idLen = data[offset + 19] & 0xFF;
                    int implLen = ((data[offset+37]&0xFF)<<8)|(data[offset+36]&0xFF);
                    
                    long icbLoc = ((data[offset+28]&0xFF)<<24)|((data[offset+27]&0xFF)<<16)|
                                  ((data[offset+26]&0xFF)<<8)|(data[offset+25]&0xFF);
                    
                    int nameStart = offset + 38 + implLen;
                    if (nameStart + idLen > data.length) continue;
                    
                    String name = parseDstring(data, nameStart, idLen);
                    
                    if (name.length() > 0 && !containsFile(name)) {
                        IsoFile f = new IsoFile();
                        f.name = name;
                        f.sector = icbLoc;
                        // Estimate size from file name pattern
                        f.size = estimateSize(name);
                        files.add(f);
                    }
                }
            }
        } catch (Exception e) { 
            System.out.println("Error parsing: " + e.getMessage());
        }
        System.out.println("Found " + files.size() + " files");
    }
    
    private long estimateSize(String name) {
        // Estimate size based on extension
        if (name.endsWith(".m2ts")) return 100 * 1024 * 1024; // ~100MB
        if (name.endsWith(".mpls")) return 10 * 1024;
        if (name.endsWith(".clpi")) return 10 * 1024;
        if (name.endsWith(".bdjo")) return 10 * 1024;
        if (name.endsWith(".jar")) return 100 * 1024;
        if (name.endsWith(".xml")) return 10 * 1024;
        return 1024;
    }
    
    private boolean containsFile(String name) {
        for (IsoFile f : files) {
            if (f.name.equals(name)) return true;
        }
        return false;
    }
    
    private String parseDstring(byte[] data, int start, int len) {
        if (len < 2) return "";
        if (data[start] == 0x10) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i + 1 < len && start + i + 1 < data.length; i += 2) {
                char c = (char)((data[start + i] << 8) | (data[start + i + 1] & 0xFF));
                if (c != 0) sb.append(c);
            }
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len && start + i < data.length; i++) {
                char c = (char)(data[start + i] & 0xFF);
                if (c >= 0x20 && c < 0x7F) sb.append(c);
            }
            return sb.toString();
        }
    }
    
    private byte[] readRange(long offset, int length) throws IOException {
        if (!isNetwork) {
            synchronized(localFile) {
                localFile.seek(offset);
                byte[] buf = new byte[length];
                int read = localFile.read(buf);
                if (read < length) return Arrays.copyOf(buf, read);
                return buf;
            }
        }
        
        URL targetUrl = (actualUrl != null) ? actualUrl : url;
        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 302 || responseCode == 301 || responseCode == 303) {
            String newUrlStr = conn.getHeaderField("Location");
            conn.disconnect();
            actualUrl = new URL(newUrlStr);
            conn = (HttpURLConnection) actualUrl.openConnection();
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
        }
        
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1 && baos.size() < length) {
            baos.write(buf, 0, Math.min(read, length - baos.size()));
        }
        is.close();
        conn.disconnect();
        return baos.toByteArray();
    }
    
    public byte[] readSectors(long sector, int count) throws IOException {
        long offset = sector * 2048L;
        return readRange(offset, count * 2048);
    }
    
    public byte[] readFileRange(long sector, long start, long length) throws IOException {
        long offset = sector * 2048L + start;
        return readRange(offset, (int) length);
    }
    
    public IsoFileReader createFileReader() { return new IsoFileReader(); }
    public List<IsoFile> getFiles() { return files; }
    public long getPartitionStart() { return partitionStart; }
    public long getPartitionLength() { return partitionLength; }
    
    public class IsoFileReader {
        public byte[] readSectors(long sector, int count) throws IOException {
            return UdfParser.this.readSectors(sector, count);
        }
    }
}
