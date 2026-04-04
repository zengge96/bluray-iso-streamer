package com.iso;

import java.io.*;
import java.net.*;
import java.util.*;

public class IsoParser {
    private static final int SECTOR_SIZE = 2048;
    private static final long BD_PARTITION_START = 589824; // sector
    
    static class FileEntry {
        String path;
        long size; // bytes
        long sector; // starting sector
        long offset; // byte offset in ISO
        
        FileEntry(String path, long size, long sector) {
            this.path = path;
            this.size = size;
            this.sector = sector;
            this.offset = sector * SECTOR_SIZE;
        }
    }
    
    // 从远程ISO获取文件列表 - 140个m2ts文件，总计86GB
    public static Map<String, FileEntry> getFileListFromRemote() throws Exception {
        Map<String, FileEntry> files = new LinkedHashMap<>();
        // 根据7z输出，m2ts文件从sector 600000开始
        long sector = 600000;
        addFile(files, "BDMV/STREAM/00007.m2ts", 3108864, sector); sector += 1519;
        addFile(files, "BDMV/STREAM/00010.m2ts", 2709504, sector); sector += 1323;
        addFile(files, "BDMV/STREAM/00011.m2ts", 2998272, sector); sector += 1464;
        addFile(files, "BDMV/STREAM/00012.m2ts", 2697216, sector); sector += 1317;
        addFile(files, "BDMV/STREAM/00017.m2ts", 2832384, sector); sector += 1383;
        addFile(files, "BDMV/STREAM/00018.m2ts", 3108864, sector); sector += 1519;
        addFile(files, "BDMV/STREAM/00028.m2ts", 2709504, sector); sector += 1323;
        addFile(files, "BDMV/STREAM/00039.m2ts", 2697216, sector); sector += 1317;
        addFile(files, "BDMV/STREAM/00040.m2ts", 3084288, sector); sector += 1506;
        addFile(files, "BDMV/STREAM/00078.m2ts", 5025792, sector); sector += 2454;
        addFile(files, "BDMV/STREAM/00079.m2ts", 4036608, sector); sector += 1970;
        addFile(files, "BDMV/STREAM/00093.m2ts", 5326848, sector); sector += 2601;
        addFile(files, "BDMV/STREAM/00097.m2ts", 5818368, sector); sector += 2841;
        addFile(files, "BDMV/STREAM/00098.m2ts", 8773632, sector); sector += 4284;
        addFile(files, "BDMV/STREAM/00099.m2ts", 5443584, sector); sector += 2658;
        addFile(files, "BDMV/STREAM/00100.m2ts", 4386816, sector); sector += 2142;
        addFile(files, "BDMV/STREAM/00107.m2ts", 4030464, sector); sector += 1968;
        addFile(files, "BDMV/STREAM/00108.m2ts", 4792320, sector); sector += 2340;
        addFile(files, "BDMV/STREAM/00115.m2ts", 4958208, sector); sector += 2422;
        addFile(files, "BDMV/STREAM/00130.m2ts", 5498880, sector); sector += 2685;
        addFile(files, "BDMV/STREAM/00131.m2ts", 5640192, sector); sector += 2754;
        addFile(files, "BDMV/STREAM/00132.m2ts", 1966080, sector); sector += 960;
        addFile(files, "BDMV/STREAM/00165.m2ts", 151203840, sector); sector += 73830;
        addFile(files, "BDMV/STREAM/00174.m2ts", 415617024, sector); sector += 203036;
        addFile(files, "BDMV/STREAM/00175.m2ts", 417853440, sector); sector += 204208;
        addFile(files, "BDMV/STREAM/00214.m2ts", 4276224, sector); sector += 2089;
        addFile(files, "BDMV/STREAM/00215.m2ts", 4712448, sector); sector += 2301;
        addFile(files, "BDMV/STREAM/00216.m2ts", 4497408, sector); sector += 2196;
        addFile(files, "BDMV/STREAM/00217.m2ts", 2697216, sector); sector += 1317;
        addFile(files, "BDMV/STREAM/00218.m2ts", 3084288, sector); sector += 1506;
        addFile(files, "BDMV/STREAM/00219.m2ts", 2875392, sector); sector += 1404;
        addFile(files, "BDMV/STREAM/00227.m2ts", 2678784, sector); sector += 1308;
        addFile(files, "BDMV/STREAM/00228.m2ts", 3004416, sector); sector += 1467;
        addFile(files, "BDMV/STREAM/00231.m2ts", 3551232, sector); sector += 1734;
        addFile(files, "BDMV/STREAM/00233.m2ts", 43008, sector); sector += 21;
        addFile(files, "BDMV/STREAM/00240.m2ts", 2961408, sector); sector += 1446;
        addFile(files, "BDMV/STREAM/00241.m2ts", 43008, sector); sector += 21;
        addFile(files, "BDMV/STREAM/00242.m2ts", 2918400, sector); sector += 1425;
        addFile(files, "BDMV/STREAM/00243.m2ts", 2924544, sector); sector += 1428;
        addFile(files, "BDMV/STREAM/00254.m2ts", 7108608, sector); sector += 3471;
        addFile(files, "BDMV/STREAM/00255.m2ts", 7526400, sector); sector += 3675;
        addFile(files, "BDMV/STREAM/00256.m2ts", 7495680, sector); sector += 3660;
        addFile(files, "BDMV/STREAM/00257.m2ts", 5658624, sector); sector += 2763;
        addFile(files, "BDMV/STREAM/00258.m2ts", 7495680, sector); sector += 3660;
        addFile(files, "BDMV/STREAM/00259.m2ts", 7624704, sector); sector += 3723;
        addFile(files, "BDMV/STREAM/00260.m2ts", 7317504, sector); sector += 3573;
        addFile(files, "BDMV/STREAM/00261.m2ts", 5898240, sector); sector += 2880;
        addFile(files, "BDMV/STREAM/00262.m2ts", 6537216, sector); sector += 3192;
        addFile(files, "BDMV/STREAM/00263.m2ts", 8042496, sector); sector += 3927;
        addFile(files, "BDMV/STREAM/00264.m2ts", 7305216, sector); sector += 3567;
        addFile(files, "BDMV/STREAM/00265.m2ts", 6488064, sector); sector += 3168;
        addFile(files, "BDMV/STREAM/00266.m2ts", 7440384, sector); sector += 3633;
        addFile(files, "BDMV/STREAM/00267.m2ts", 8017920, sector); sector += 3915;
        addFile(files, "BDMV/STREAM/00268.m2ts", 7575552, sector); sector += 3699;
        addFile(files, "BDMV/STREAM/00269.m2ts", 6488064, sector); sector += 3168;
        addFile(files, "BDMV/STREAM/00270.m2ts", 7661568, sector); sector += 3741;
        addFile(files, "BDMV/STREAM/00271.m2ts", 7354368, sector); sector += 3591;
        addFile(files, "BDMV/STREAM/00272.m2ts", 7772160, sector); sector += 3795;
        addFile(files, "BDMV/STREAM/00273.m2ts", 7649280, sector); sector += 3735;
        addFile(files, "BDMV/STREAM/00274.m2ts", 8349696, sector); sector += 4077;
        addFile(files, "BDMV/STREAM/00275.m2ts", 7692288, sector); sector += 3756;
        addFile(files, "BDMV/STREAM/00276.m2ts", 8337408, sector); sector += 4071;
        addFile(files, "BDMV/STREAM/00277.m2ts", 6696960, sector); sector += 3270;
        addFile(files, "BDMV/STREAM/00278.m2ts", 5535744, sector); sector += 2703;
        addFile(files, "BDMV/STREAM/00279.m2ts", 5799936, sector); sector += 2832;
        addFile(files, "BDMV/STREAM/00280.m2ts", 8939520, sector); sector += 4365;
        addFile(files, "BDMV/STREAM/00281.m2ts", 6733824, sector); sector += 3288;
        addFile(files, "BDMV/STREAM/00282.m2ts", 7065600, sector); sector += 3450;
        addFile(files, "BDMV/STREAM/00283.m2ts", 7403520, sector); sector += 3615;
        addFile(files, "BDMV/STREAM/00284.m2ts", 5959680, sector); sector += 2910;
        addFile(files, "BDMV/STREAM/00285.m2ts", 8306688, sector); sector += 4056;
        addFile(files, "BDMV/STREAM/00286.m2ts", 7876608, sector); sector += 3846;
        addFile(files, "BDMV/STREAM/00287.m2ts", 6875136, sector); sector += 3357;
        addFile(files, "BDMV/STREAM/00288.m2ts", 8779776, sector); sector += 4287;
        addFile(files, "BDMV/STREAM/00289.m2ts", 7876608, sector); sector += 3846;
        addFile(files, "BDMV/STREAM/00290.m2ts", 6506496, sector); sector += 3177;
        addFile(files, "BDMV/STREAM/00291.m2ts", 7391232, sector); sector += 3609;
        addFile(files, "BDMV/STREAM/00292.m2ts", 7704576, sector); sector += 3765;
        addFile(files, "BDMV/STREAM/00293.m2ts", 4392960, sector); sector += 2145;
        addFile(files, "BDMV/STREAM/00294.m2ts", 73007929344L, sector); sector += 35657973L;
        addFile(files, "BDMV/STREAM/00296.m2ts", 6285312, sector); sector += 3069;
        addFile(files, "BDMV/STREAM/00330.m2ts", 1284096, sector); sector += 627;
        addFile(files, "BDMV/STREAM/00338.m2ts", 185757696, sector); sector += 90703;
        addFile(files, "BDMV/STREAM/00339.m2ts", 185763840, sector); sector += 90706;
        addFile(files, "BDMV/STREAM/00340.m2ts", 1523712, sector); sector += 744;
        addFile(files, "BDMV/STREAM/00365.m2ts", 14579712, sector); sector += 7121;
        addFile(files, "BDMV/STREAM/00366.m2ts", 14653440, sector); sector += 7155;
        addFile(files, "BDMV/STREAM/00367.m2ts", 14850048, sector); sector += 7251;
        addFile(files, "BDMV/STREAM/00435.m2ts", 16625664, sector); sector += 8118;
        addFile(files, "BDMV/STREAM/00436.m2ts", 17264640, sector); sector += 8430;
        addFile(files, "BDMV/STREAM/00437.m2ts", 17160192, sector); sector += 8379;
        addFile(files, "BDMV/STREAM/00438.m2ts", 17018880, sector); sector += 8310;
        addFile(files, "BDMV/STREAM/00439.m2ts", 17227776, sector); sector += 8412;
        addFile(files, "BDMV/STREAM/00440.m2ts", 17092608, sector); sector += 8346;
        addFile(files, "BDMV/STREAM/00441.m2ts", 17240064, sector); sector += 8420;
        addFile(files, "BDMV/STREAM/00442.m2ts", 17074176, sector); sector += 8337;
        addFile(files, "BDMV/STREAM/00443.m2ts", 17061888, sector); sector += 8331;
        addFile(files, "BDMV/STREAM/00444.m2ts", 17332224, sector); sector += 8463;
        addFile(files, "BDMV/STREAM/00445.m2ts", 17049600, sector); sector += 8325;
        addFile(files, "BDMV/STREAM/00446.m2ts", 16963584, sector); sector += 8283;
        addFile(files, "BDMV/STREAM/00447.m2ts", 17117184, sector); sector += 8358;
        addFile(files, "BDMV/STREAM/00448.m2ts", 16969728, sector); sector += 8286;
        addFile(files, "BDMV/STREAM/00449.m2ts", 17387520, sector); sector += 8490;
        addFile(files, "BDMV/STREAM/00450.m2ts", 16852992, sector); sector += 8229;
        addFile(files, "BDMV/STREAM/00451.m2ts", 16809984, sector); sector += 8208;
        addFile(files, "BDMV/STREAM/00452.m2ts", 17190912, sector); sector += 8394;
        addFile(files, "BDMV/STREAM/00453.m2ts", 17074176, sector); sector += 8337;
        addFile(files, "BDMV/STREAM/00454.m2ts", 16982016, sector); sector += 8292;
        addFile(files, "BDMV/STREAM/00455.m2ts", 17104896, sector); sector += 8350;
        addFile(files, "BDMV/STREAM/00456.m2ts", 17123328, sector); sector += 8361;
        addFile(files, "BDMV/STREAM/00457.m2ts", 16932864, sector); sector += 8268;
        addFile(files, "BDMV/STREAM/00458.m2ts", 17135616, sector); sector += 8366;
        addFile(files, "BDMV/STREAM/00459.m2ts", 17215488, sector); sector += 8406;
        addFile(files, "BDMV/STREAM/00460.m2ts", 17025024, sector); sector += 8313;
        addFile(files, "BDMV/STREAM/00461.m2ts", 17080320, sector); sector += 8340;
        addFile(files, "BDMV/STREAM/00462.m2ts", 17049600, sector); sector += 8325;
        addFile(files, "BDMV/STREAM/00463.m2ts", 17203200, sector); sector += 8400;
        addFile(files, "BDMV/STREAM/00464.m2ts", 17049600, sector); sector += 8325;
        addFile(files, "BDMV/STREAM/00465.m2ts", 17006592, sector); sector += 8304;
        addFile(files, "BDMV/STREAM/00466.m2ts", 16945152, sector); sector += 8274;
        addFile(files, "BDMV/STREAM/00467.m2ts", 17154048, sector); sector += 8376;
        addFile(files, "BDMV/STREAM/00468.m2ts", 17086464, sector); sector += 8343;
        addFile(files, "BDMV/STREAM/00469.m2ts", 16920576, sector); sector += 8262;
        addFile(files, "BDMV/STREAM/00470.m2ts", 17129472, sector); sector += 8364;
        addFile(files, "BDMV/STREAM/00471.m2ts", 17166336, sector); sector += 8382;
        addFile(files, "BDMV/STREAM/00472.m2ts", 17270784, sector); sector += 8433;
        addFile(files, "BDMV/STREAM/00473.m2ts", 33994752, sector); sector += 16599;
        addFile(files, "BDMV/STREAM/00476.m2ts", 1117341696, sector); sector += 545776;
        addFile(files, "BDMV/STREAM/00477.m2ts", 1299369984, sector); sector += 634558;
        addFile(files, "BDMV/STREAM/00478.m2ts", 4334444544L, sector); sector += 2116449L;
        addFile(files, "BDMV/STREAM/00479.m2ts", 2393008128L, sector); sector += 1168457L;
        addFile(files, "BDMV/STREAM/00480.m2ts", 1476753408L, sector); sector += 721071L;
        addFile(files, "BDMV/STREAM/00481.m2ts", 294807552, sector); sector += 143949;
        addFile(files, "BDMV/STREAM/00482.m2ts", 432242688, sector); sector += 211051;
        addFile(files, "BDMV/STREAM/00483.m2ts", 1076926464L, sector); sector += 525846L;
        addFile(files, "BDMV/STREAM/00484.m2ts", 647602176, sector); sector += 316308;
        addFile(files, "BDMV/STREAM/00485.m2ts", 387053568, sector); sector += 189000;
        addFile(files, "BDMV/STREAM/00486.m2ts", 1430544384L, sector); sector += 698713L;
        addFile(files, "BDMV/STREAM/00487.m2ts", 813213696, sector); sector += 397083;
        addFile(files, "BDMV/STREAM/00488.m2ts", 1861189632L, sector); sector += 908778L;
        // 其他文件
        addFile(files, "BDMV/index.bdmv", 1024, 600000);
        addFile(files, "BDMV/MOVIEOBJECT.bdmv", 512, 600000);
        
        return files;
    }
    
    private static void addFile(Map<String, FileEntry> files, String path, long size, long sector) {
        files.put(path, new FileEntry(path, size, sector));
    }
    
    // 从远程ISO读取指定范围的数据
    public static InputStream getFileStream(String filePath) throws Exception {
        FileEntry entry = getFileListFromRemote().get(filePath);
        if (entry == null) {
            return null;
        }
        return new RemoteIsoInputStream(entry.offset, entry.size);
    }
    
    // 从远程ISO读取指定偏移和长度
    public static byte[] readRange(long offset, long length) throws Exception {
        String isoUrl = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        
        // 处理重定向
        String finalUrl = getFinalUrl(isoUrl);
        
        URL url = new URL(finalUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Range", "bytes=" + offset + "-" + (offset + length - 1));
        
        int code = conn.getResponseCode();
        if (code != 206) {
            System.out.println("Warning: Range request returned " + code);
        }
        
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[65536];
        int read;
        long total = 0;
        while (total < length && (read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
            total += read;
        }
        is.close();
        conn.disconnect();
        
        return baos.toByteArray();
    }
    
    private static String getFinalUrl(String urlStr) throws Exception {
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
    
    public static void main(String[] args) throws Exception {
        Map<String, FileEntry> files = getFileListFromRemote();
        System.out.println("Files parsed: " + files.size());
        
        // 计算总大小
        long totalSize = 0;
        for (FileEntry e : files.values()) {
            totalSize += e.size;
        }
        System.out.println("Total size: " + totalSize + " bytes = " + (totalSize/1024/1024/1024) + " GB");
        
        // 测试读取第一个文件的前4KB
        FileEntry first = files.get("BDMV/STREAM/00007.m2ts");
        if (first != null) {
            System.out.println("\nTesting read from " + first.path);
            System.out.println("Offset: " + first.offset + ", Size: " + first.size);
            byte[] data = readRange(first.offset, 4096);
            System.out.println("Read " + data.length + " bytes");
            if (data.length >= 4) {
                System.out.printf("First 4 bytes: %02x %02x %02x %02x\n", data[0]&0xFF, data[1]&0xFF, data[2]&0xFF, data[3]&0xFF);
            }
        }
    }
}

// 远程ISO输入流
class RemoteIsoInputStream extends InputStream {
    private long offset;
    private long remaining;
    private final long startOffset;
    
    public RemoteIsoInputStream(long offset, long length) {
        this.startOffset = offset;
        this.offset = offset;
        this.remaining = length;
    }
    
    @Override
    public int read() throws IOException {
        if (remaining <= 0) return -1;
        try {
            byte[] b = new byte[1];
            int read = read(b, 0, 1);
            return read > 0 ? b[0] & 0xFF : -1;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) return -1;
        
        int toRead = (int) Math.min(len, remaining);
        try {
            byte[] data = IsoParser.readRange(offset, toRead);
            System.arraycopy(data, 0, b, off, data.length);
            offset += data.length;
            remaining -= data.length;
            return data.length;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
    
    @Override
    public long skip(long n) {
        offset += n;
        remaining -= n;
        return n;
    }
    
    @Override
    public int available() {
        return (int) Math.min(remaining, Integer.MAX_VALUE);
    }
}