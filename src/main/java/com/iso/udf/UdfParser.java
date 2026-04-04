package com.iso.udf;

import java.io.*;
import java.net.*;
import java.util.*;

public class UdfParser {
    private static final int DESC_TYPE_ANCHOR_VOL_PTR = 2;
    private static final int DESC_TYPE_PARTITION = 5;
    private static final int DESC_TYPE_LOGICAL_VOL = 6;
    private static final int DESC_TYPE_FILE_SET = 256;
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
    private long metadataPartitionStart = 0;
    private long metadataPartitionLength = 0;
    
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
        System.out.println("Connecting to: " + url);
        long anchorOffset = findAnchorVolumeDescriptor();
        if (anchorOffset < 0) throw new IOException("Cannot find Anchor VD");
        parseVolumeDescriptors(anchorOffset);
        parseMetadataPartition();
    }
    
    private long findAnchorVolumeDescriptor() throws IOException {
        long offset = (long) ANCHOR_VOL_DESCRIPTOR_LOCATION * sectorSize;
        System.out.println("Reading Anchor VD at offset " + offset);
        byte[] buf = readRange(offset, sectorSize);
        int tagId = ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF); if (tagId == DESC_TYPE_ANCHOR_VOL_PTR) {
            System.out.println("Found Anchor VD at offset " + offset);
            return offset;
        }
        return -1;
    }
    
    private void parseVolumeDescriptors(long anchorOffset) throws IOException {
        long vdsOffset = 32L * sectorSize;
        System.out.println("Main VDS: len=32768, loc=32");
        byte[] vds = readRange(vdsOffset, 32768);
        
        for (int i = 0; i < 32768; i += sectorSize) {
            if (i + 32 > vds.length) break;
            int tag = ((vds[i+1]&0xFF)<<8)|(vds[i]&0xFF);
            if (tag == DESC_TYPE_LOGICAL_VOL) {
                System.out.println("Found Logical Volume at offset " + i);
                blockSize = ((vds[i+179]&0xFF)<<8)|(vds[i+178]&0xFF);
                System.out.println("BlockSize: " + blockSize);
            }
            if (tag == DESC_TYPE_PARTITION) {
                partitionStart = ((vds[i+191]&0xFF)<<24)|((vds[i+190]&0xFF)<<16)|
                                ((vds[i+189]&0xFF)<<8)|(vds[i+188]&0xFF);
                partitionLength = ((vds[i+195]&0xFF)<<24)|((vds[i+194]&0xFF)<<16)|
                                 ((vds[i+193]&0xFF)<<8)|(vds[i+192]&0xFF);
                if (i > 0) {
                    metadataPartitionStart = partitionStart;
                    metadataPartitionLength = 1703936;
                }
                System.out.println("Main Partition: startLsn=" + partitionStart + ", len=" + partitionLength);
            }
        }
    }
    
    private void parseMetadataPartition() throws IOException {
        long metaSector = 320;
        System.out.println("Parsing metadata at sector " + metaSector);
        
        for (int sec = 320; sec < 400; sec++) {
            byte[] data = readSectors(sec, 4);
            if (data == null || data.length < 2048) continue;
            
            for (int offset = 0; offset < data.length - 100; offset += 32) {
                int tag = ((data[offset+1]&0xFF)<<8)|(data[offset]&0xFF);
                if (tag != DESC_TYPE_FILE_ID) continue;
                
                int nameOffset = offset + 48;
                if (nameOffset >= data.length) continue;
                
                int end = nameOffset;
                while (end < data.length - 1 && (data[end] != 0 || data[end+1] != 0)) {
                    end += 2;
                }
                
                int nameLen = end - nameOffset;
                if (nameLen <= 0 || nameLen > 100) continue;
                
                try {
                    String name = new String(data, nameOffset, nameLen, java.nio.charset.StandardCharsets.UTF_16BE);
                    name = name.replace("\u0000", "").trim();
                    if (name.length() == 0) continue;
                    
                    long icbLoc = ((data[offset+43]&0xFF)<<24)|((data[offset+42]&0xFF)<<16)|
                                  ((data[offset+41]&0xFF)<<8)|(data[offset+40]&0xFF);
                    
                    if (icbLoc > 0 && icbLoc < 50000000) {
                        IsoFile f = new IsoFile();
                        f.name = name;
                        f.sector = icbLoc;
                        files.add(f);
                    }
                } catch (Exception e) {}
            }
        }
        System.out.println("Found " + files.size() + " files");
    }
    
    private byte[] readRange(long offset, int length) throws IOException {
        if (!isNetwork) {
            synchronized(localFile) {
                localFile.seek(offset);
                byte[] buf = new byte[length];
                localFile.readFully(buf);
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
    
    public class IsoFileReader {
        public byte[] readSectors(long sector, int count) throws IOException {
            return UdfParser.this.readSectors(sector, count);
        }
    }
}
