package com.imagefixer.app;

import java.io.Serializable;
import java.util.Date;

/**
 * 扫描文件信息的数据结构类，用于存储所有扫描文件的详细信息
 * 实现Serializable接口以便能够通过Intent传递
 */
public class ScanFileInfo implements Serializable {
    private static final long serialVersionUID = 2L;
    
    private String filePath;           // 文件路径
    private long originalTime;         // 原始修改时间
    private long fixedTime;            // 修正后的时间（如果未修正则与原始时间相同）
    private boolean isFixed;           // 是否被修正
    
    /**
     * 构造函数 - 未修正的文件
     * @param filePath 文件路径
     * @param originalTime 原始修改时间
     */
    public ScanFileInfo(String filePath, long originalTime) {
        this.filePath = filePath;
        this.originalTime = originalTime;
        this.fixedTime = originalTime; // 未修正时，修正时间等于原始时间
        this.isFixed = false;
    }
    
    /**
     * 构造函数 - 已修正的文件
     * @param filePath 文件路径
     * @param originalTime 原始修改时间
     * @param fixedTime 修正后的时间
     */
    public ScanFileInfo(String filePath, long originalTime, long fixedTime) {
        this.filePath = filePath;
        this.originalTime = originalTime;
        this.fixedTime = fixedTime;
        this.isFixed = true;
    }
    
    /**
     * 获取文件路径
     * @return 文件的绝对路径
     */
    public String getFilePath() {
        return filePath;
    }
    
    /**
     * 获取原始修改时间
     * @return 原始修改时间的时间戳
     */
    public long getOriginalTime() {
        return originalTime;
    }
    
    /**
     * 获取修正后的时间
     * @return 修正后的时间戳
     */
    public long getFixedTime() {
        return fixedTime;
    }
    
    /**
     * 判断文件是否被修正
     * @return true如果文件被修正，false否则
     */
    public boolean isFixed() {
        return isFixed;
    }
    
    /**
     * 获取原始修改时间的Date对象
     * @return 原始修改时间
     */
    public Date getOriginalDate() {
        return new Date(originalTime);
    }
    
    /**
     * 获取修正后的时间的Date对象
     * @return 修正后的时间
     */
    public Date getFixedDate() {
        return new Date(fixedTime);
    }
    
    @Override
    public String toString() {
        return "ScanFileInfo{" +
                "filePath='" + filePath + '\'' +
                ", originalTime=" + originalTime +
                ", fixedTime=" + fixedTime +
                ", isFixed=" + isFixed +
                '}';
    }
}