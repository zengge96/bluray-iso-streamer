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
    private final RandomAccessFile localFile;
    private boolean isNetwork;
    private int sectorSize = 2048;
    
    private long partitionStart = 0;
    private long partitionLength = 0;
    private int blockSize = 2048;
    
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
        long offset = (long) ANCHOR_VOL_DESCRIPTOR_LOCATION * sectorSize;
        byte[] buf = readRange(offset, sectorSize);
        int tagId = ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF);
        if (tagId == DESC_TYPE_ANCHOR_VOL_PTR) {
            System.out.println("Found Anchor VD at offset " + offset);
            return offset;
        }
        return -1;
    }
    
    private void parseVolumeDescriptors(long anchorOffset) throws IOException {
        long vdsOffset = 32L * sectorSize;
        byte[] vds = readRange(vdsOffset, 32768);
        
        for (int i = 0; i < 32768; i += sectorSize) {
            if (i + 32 > vds.length) break;
            int tag = ((vds[i+1]&0xFF)<<8)|(vds[i]&0xFF);
            if (tag == DESC_TYPE_LOGICAL_VOL) {
                blockSize = ((vds[i+179]&0xFF)<<8)|(vds[i+178]&0xFF);
            }
            if (tag == DESC_TYPE_PARTITION) {
                partitionStart = ((vds[i+191]&0xFF)<<24)|((vds[i+190]&0xFF)<<16)|
                                ((vds[i+189]&0xFF)<<8)|(vds[i+188]&0xFF);
                partitionLength = ((vds[i+195]&0xFF)<<24)|((vds[i+194]&0xFF)<<16)|
                                 ((vds[i+193]&0xFF)<<8)|(vds[i+192]&0xFF);
                System.out.println("Partition: start=" + partitionStart + ", len=" + partitionLength);
            }
        }
    }
    
    private void parseMetadataPartition() throws IOException {
        System.out.println("Parsing metadata at sector 320...");
        
        // Read more sectors for file list
        for (int sec = 320; sec < 500; sec++) {
            byte[] data = readSectors(sec, 4);
            if (data == null || data.length < 38) continue;
            
            for (int offset = 0; offset < data.length - 38; offset += 4) {
                int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
                if (tag != DESC_TYPE_FILE_ID) continue;
                
                int idLen = data[offset + 19] & 0xFF;
                int implLen = ((data[offset+37]&0xFF)<<8)|(data[offset+36]&0xFF);
                
                // ICB at offset +20, location at +8 from there
                long icbLoc = ((data[offset+28]&0xFF)<<24)|((data[offset+27]&0xFF)<<16)|
                              ((data[offset+26]&0xFF)<<8)|(data[offset+25]&0xFF);
                
                int nameStart = offset + 38 + implLen;
                if (nameStart + idLen > data.length) continue;
                
                String name = parseDstring(data, nameStart, idLen);
                
                if (name.length() > 0 && !files.contains(name)) {
                    IsoFile f = new IsoFile();
                    f.name = name;
                    f.sector = icbLoc;
                    files.add(f);
                }
            }
        }
        System.out.println("Found " + files.size() + " files");
    }
    
    private String parseDstring(byte[] data, int start, int len) {
        if (len < 2) return "";
        
        // Check for character set identifier
        if (data[start] == 0x10) {
            // UTF-16BE
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i + 1 < len && start + i + 1 < data.length; i += 2) {
                char c = (char)((data[start + i] << 8) | (data[start + i + 1] & 0xFF));
                if (c != 0) sb.append(c);
            }
            return sb.toString();
        } else {
            // ASCII
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
                if (read < length) {
                    return Arrays.copyOf(buf, read);
                }
                return buf;
            }
        }
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        
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
    
    public IsoFileReader createFileReader() {
        return new IsoFileReader();
    }
    
    public List<IsoFile> getFiles() { return files; }
    public long getPartitionStart() { return partitionStart; }
    public long getPartitionLength() { return partitionLength; }
    
    public class IsoFileReader {
        public byte[] readSectors(long sector, int count) throws IOException {
            return UdfParser.this.readSectors(sector, count);
        }
    }
}
