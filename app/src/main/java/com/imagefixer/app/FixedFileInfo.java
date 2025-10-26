package com.imagefixer.app;

import java.io.Serializable;
import java.util.Date;

/**
 * 修正文件信息的数据结构类，用于存储文件修正前后的信息
 * 实现Serializable接口以便能够通过Intent传递
 */
public class FixedFileInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String filePath;           // 文件路径
    private long originalTime;         // 原始修改时间
    private long fixedTime;            // 修正后的时间
    
    /**
     * 构造函数
     * @param filePath 文件路径
     * @param originalTime 原始修改时间
     * @param fixedTime 修正后的时间
     */
    public FixedFileInfo(String filePath, long originalTime, long fixedTime) {
        this.filePath = filePath;
        this.originalTime = originalTime;
        this.fixedTime = fixedTime;
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
        return "FixedFileInfo{" +
                "filePath='" + filePath + '\'' +
                ", originalTime=" + originalTime +
                ", fixedTime=" + fixedTime +
                '}';
    }
}