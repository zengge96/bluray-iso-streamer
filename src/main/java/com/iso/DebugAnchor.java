package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class DebugAnchor {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        
        // Debug read
        java.lang.reflect.Method m = UdfParser.class.getDeclaredMethod("readRange", long.class, int.class);
        m.setAccessible(true);
        
        byte[] buf = (byte[]) m.invoke(parser, 524288L, 16);
        
        int tagId = ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF);
        System.out.println("Tag at 524288: " + tagId + " (expected 2)");
        System.out.println("First bytes: " + String.format("%02X %02X %02X %02X", buf[0]&0xFF, buf[1]&0xFF, buf[2]&0xFF, buf[3]&0xFF));
    }
}
