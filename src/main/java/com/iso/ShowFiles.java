package com.iso;
import com.iso.udf.*;
import java.nio.file.*;

public class ShowFiles {
    public static void main(String[] args) throws Exception {
        String url = new String(Files.readAllBytes(Paths.get("/root/.openclaw/workspace/url.txt"))).trim();
        UdfParser parser = new UdfParser(url);
        parser.parse();
        
        System.out.println("\n=== Files Found ===");
        for (IsoFile f : parser.getFiles()) {
            System.out.println(f.name + " -> sector " + f.sector);
        }
    }
}
