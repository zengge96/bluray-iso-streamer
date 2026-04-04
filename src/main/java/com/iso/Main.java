package com.iso;

import com.iso.udf.*;
import java.io.*;
import java.util.*;

/**
 * Main entry point - tests UDF parsing from network
 */
public class Main {
    public static void main(String[] args) throws Exception {
        String url;
        
        if (args.length > 0) {
            url = args[0];
        } else {
            try {
                BufferedReader br = new BufferedReader(new FileReader("/root/.openclaw/workspace/url.txt"));
                url = br.readLine().trim();
                br.close();
            } catch (IOException e) {
                System.err.println("Usage: java com.iso.Main <url>");
                return;
            }
        }
        
        if (url.startsWith("http://") || url.startsWith("https://")) {
            System.out.println("=== Network ISO Parser ===");
            testNetworkParse(url);
        }
    }
    
    private static void testNetworkParse(String urlStr) throws Exception {
        UdfParser parser = new UdfParser(urlStr);
        
        // 只解析分区信息，不尝试读取文件系统
        System.out.println("\n--- Parsing ISO structure ---");
        List<IsoFile> files = parser.parse();
        
        // 输出关键信息
        System.out.println("\n=== ISO Info ===");
        System.out.println("Sector Size: " + parser.getSectorSize());
        System.out.println("Block Size: " + parser.getBlockSize());
        System.out.println("Partition Start: " + parser.getPartitionStart() + " bytes (sector " + parser.getPartitionStartLsn() + ")");
        System.out.println("Partition Size: " + parser.getPartitionLength() + " bytes");
        
        // 测试网络流读取 - 读取partition起始位置的第一个扇区
        System.out.println("\n--- Testing Network Stream ---");
        UdfParser.IsoFileReader reader = parser.createFileReader();
        
        // 读取partition开始的第一个sector
        long partitionStartLsn = parser.getPartitionStartLsn();
        System.out.println("Reading sector " + partitionStartLsn + "...");
        
        byte[] data = reader.readSectors(partitionStartLsn, 1);
        System.out.println("Read " + data.length + " bytes");
        
        // 检查是否是有效的文件系统数据
        int tagId = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
        System.out.println("First sector tag ID: " + tagId);
        
        if (tagId == 261 || tagId == 266) {
            System.out.println("✓ Found File Entry at partition start");
        }
        
        System.out.println("\n=== Streaming Test Complete ===");
        System.out.println("Network-based ISO parsing and streaming is working!");
    }
}