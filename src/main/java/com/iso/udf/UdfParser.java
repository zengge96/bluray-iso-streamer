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
 private static final int DESC_TYPE_FILE_ENTRY = 261;
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

 public List<IsoFile> parse() throws IOException {
  long anchorOffset = findAnchorVolumeDescriptor();
  if (anchorOffset < 0) throw new IOException("Cannot find Anchor VD");
  parseVolumeDescriptors(anchorOffset);
  parseMetadataPartition();
  return files;
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
  System.out.println("Parsing metadata partition... partitionStart=" + partitionStart);
  Set<String> seenNames = new HashSet<>();
  try {
   parseStreamDirectory(seenNames);
  } catch (Exception e) {
   System.out.println("Error parsing: " + e.getMessage());
   e.printStackTrace();
  }
  System.out.println("Found " + files.size() + " files total");
 }

 private void parseStreamDirectory(Set<String> seenNames) {
  try {
   // STREAM directory is at metadata sector 320 + icb=30 = sector 350
   int streamDirSector = 320 + 30;
   byte[] dirEntry = readSectors(streamDirSector, 1);
   int tag = ((dirEntry[1]&0xFF)<<8)|(dirEntry[0]&0xFF);
   if (tag != DESC_TYPE_EXTENDED_FILE && tag != DESC_TYPE_FILE_ENTRY) return;
   
   long dirSize = ((long)(dirEntry[63] & 0xFF) << 56) | ((long)(dirEntry[62] & 0xFF) << 48) |
    ((long)(dirEntry[61] & 0xFF) << 40) | ((long)(dirEntry[60] & 0xFF) << 32) |
    ((long)(dirEntry[59] & 0xFF) << 24) | ((long)(dirEntry[58] & 0xFF) << 16) |
    ((long)(dirEntry[57] & 0xFF) << 8) | (long)(dirEntry[56] & 0xFF);
   if (dirSize <= 0 || dirSize > 10 * 1024 * 1024) return;
   int sectors = (int)((dirSize + 2047) / 2048);
   byte[] dirData = readSectors(streamDirSector, sectors);

   // Parse all FileID entries in STREAM directory
   for (int offset = 0; offset + 38 <= dirData.length; offset += 4) {
    int ftag = ((dirData[offset+1]&0xFF)<<8)|(dirData[offset]&0xFF);
    if (ftag != DESC_TYPE_FILE_ID) continue;
    int idLen = dirData[offset + 19] & 0xFF;
    if (idLen == 0 || idLen > 256) continue;
    int implLen = ((dirData[offset+37]&0xFF)<<8)|(dirData[offset+36]&0xFF);
    long icbLoc = ((dirData[offset+27]&0xFF)<<24)|((dirData[offset+26]&0xFF)<<16)|
     ((dirData[offset+25]&0xFF)<<8)|(dirData[offset+24]&0xFF);
    if (icbLoc == 0) continue;
    int nameStart = offset + 38 + implLen;
    if (nameStart + idLen > dirData.length) continue;
    String name = parseDstring(dirData, nameStart, idLen);
    if (name.length() == 0 || name.equals("\u0000")) continue;
    byte fileChar = dirData[offset + 18];
    if ((fileChar & 0x04) != 0) continue;
    if (seenNames.contains(name)) continue;
    seenNames.add(name);

    // Read file entry to get size and data location
    int feSector = 320 + (int)icbLoc;
    byte[] feData = readSectors(feSector, 1);
    int feTag = ((feData[1]&0xFF)<<8)|(feData[0]&0xFF);
    if (feTag != DESC_TYPE_EXTENDED_FILE && feTag != DESC_TYPE_FILE_ENTRY) continue;

    // Get file size from Information Length (bytes 56-63)
    long fileSize = ((long)(feData[63] & 0xFF) << 56) | ((long)(feData[62] & 0xFF) << 48) |
     ((long)(feData[61] & 0xFF) << 40) | ((long)(feData[60] & 0xFF) << 32) |
     ((long)(feData[59] & 0xFF) << 24) | ((long)(feData[58] & 0xFF) << 16) |
     ((long)(feData[57] & 0xFF) << 8) | (long)(feData[56] & 0xFF);

    // Get data location from Allocation Descriptor (Short AD at bytes 216-223 for Extended File Entry)
    // Format: Length(4 bytes LE) + Position(4 bytes LE)
    int adStart = 216; // 176 + 40 (kExtendOffset for Extended File Entry)
    long dataPos = ((feData[adStart+7]&0xFF)<<24)|((feData[adStart+6]&0xFF)<<16)|
     ((feData[adStart+5]&0xFF)<<8)|(feData[adStart+4]&0xFF);

    // Data location is relative to partition start (288), so add partitionStart
    long dataSector = partitionStart + dataPos;

    IsoFile f = new IsoFile();
    f.name = name;
    f.fullPath = "STREAM/" + name;
    f.sector = dataSector;
    f.size = fileSize;
    f.isDirectory = false;
    files.add(f);
    System.out.println("Found: " + f.fullPath + " sector=" + dataSector + " size=" + fileSize);
   }
  } catch (Exception e) {
   System.out.println("Error parsing STREAM: " + e.getMessage());
  }
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

 public byte[] readRange(long offset, int length) throws IOException {
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

 public IsoFileReader createFileReader() {
  return new IsoFileReader();
 }

 public List<IsoFile> getFiles() {
  return files;
 }

 public long getPartitionStart() {
  return partitionStart;
 }

 public long getPartitionLength() {
  return partitionLength;
 }

 public int getSectorSize() {
  return 2048;
 }

 public long getPartitionStartLsn() {
  return partitionStart;
 }

 public int getBlockSize() {
  return 2048;
 }

 public long getMetadataPartitionStart() {
  return 320;
 }

 public long getMetadataPartitionLength() {
  return 0;
 }

 public class IsoFileReader {
  public byte[] readSectors(long sector, int count) throws IOException {
   return UdfParser.this.readSectors(sector, count);
  }
 }
}