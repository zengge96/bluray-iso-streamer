package com.iso.udf;

import java.util.ArrayList;
import java.util.List;

/**
 * ISO内封文件
 */
public class IsoFile {
    private String name;
    private String path;
    private long size;
    private long startSector;
    private int partitionRef;
    private boolean isDirectory;
    private long physicalOffset; // 在ISO文件中的物理偏移
    private List<IsoFile> children = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getStartSector() {
        return startSector;
    }

    public void setStartSector(long startSector) {
        this.startSector = startSector;
    }

    public int getPartitionRef() {
        return partitionRef;
    }

    public void setPartitionRef(int partitionRef) {
        this.partitionRef = partitionRef;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public long getPhysicalOffset() {
        return physicalOffset;
    }

    public void setPhysicalOffset(long physicalOffset) {
        this.physicalOffset = physicalOffset;
    }

    public List<IsoFile> getChildren() {
        return children;
    }

    public void setChildren(List<IsoFile> children) {
        this.children = children;
    }

    @Override
    public String toString() {
        return String.format("%s [%s, %d bytes, sector=%d]", 
            name, isDirectory ? "DIR" : "FILE", size, startSector);
    }
}