package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class TestNet {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        System.out.println("Parsing: " + url);
        
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        System.out.println("Files: " + parser.getFiles().size());
        
        // Show first few
        int count = 0;
        for (IsoFile f : parser.getFiles()) {
            System.out.println(f.name + " -> " + f.sector);
            if (++count > 15) break;
        }
    }
}
