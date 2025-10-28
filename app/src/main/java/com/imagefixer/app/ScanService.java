/**
 * Android Images time fixer (手机照片时间修正器)
 * 
 * 版权所有 © 2023 Android Images time fixer 保留所有权利
 * 
 * 本项目采用MIT许可证
 * 版本：0.5.0
 * 更新时间：2024-12-01
 * 
 * 主要开发者：disminde.lee limingrui emeric
 * 邮箱：2714845535@qq.com kometo@gmail.com
 * GitHub：https://github.com/disminde
 */
package com.imagefixer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import com.imagefixer.app.utils.LogUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.Serializable;
import android.media.ExifInterface;

public class ScanService extends Service {

    private static final String TAG = "ScanService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "scan_channel";
    private static final int MAX_SCAN_DEPTH = 10; // 最大扫描深度限制
    private static final int BATCH_SIZE = 5; // 批处理大小
    private static final int THREAD_POOL_SIZE = Math.min(4, Runtime.getRuntime().availableProcessors()); // 限制最大线程数，避免过多并发
    private static final long MIN_NOTIFICATION_INTERVAL_MS = 1000; // 通知最小更新间隔（毫秒）
    private static final long MEMORY_CHECK_INTERVAL_MS = 5000; // 内存检查间隔
    private static final float LOW_MEMORY_THRESHOLD = 0.15f; // 低内存阈值（可用内存低于15%时触发优化）
    private static final int FILE_INFO_BATCH_SIZE = 10; // 文件信息批处理大小
    private static final long FILE_INFO_BROADCAST_INTERVAL_MS = 1000; // 文件信息广播间隔（毫秒）

    // 广播动作
    public static final String ACTION_SCAN_PROGRESS = "com.imagefixer.app.ACTION_SCAN_PROGRESS";
    public static final String ACTION_SCAN_COMPLETED = "com.imagefixer.app.ACTION_SCAN_COMPLETED";
    public static final String ACTION_SCAN_ERROR = "com.imagefixer.app.ACTION_SCAN_ERROR";
    public static final String ACTION_FILE_INFO_UPDATE = "com.imagefixer.app.ACTION_FILE_INFO_UPDATE";

    // 广播额外数据
    public static final String EXTRA_SCANNED_COUNT = "scanned_count";
    public static final String EXTRA_FIXED_COUNT = "fixed_count";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";

    public static final String EXTRA_FILE_INFO = "file_info";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_TOTAL_FILES = "total_files";
    public static final String EXTRA_SCANNED_FILES_LIST = "scanned_files_list"; // 所有扫描文件列表

    private Looper serviceLooper;
    private ServiceHandler serviceHandler;
    private NotificationManager notificationManager;
    private ExecutorService executorService; // 线程池
    private Handler mainHandler; // 用于在主线程更新通知
    private ScheduledExecutorService memoryMonitorService; // 内存监控线程池

    private long lastNotificationUpdateTime = 0; // 上次通知更新时间
    private int lastScannedCount = 0; // 上次通知中的扫描计数
    private int lastFixedCount = 0; // 上次通知中的修复计数
    private boolean notificationUpdatePending = false; // 是否有待处理的通知更新
    private boolean isLowMemoryMode = false; // 是否处于低内存模式
    private List<ScanFileInfo> pendingFileInfos = new ArrayList<>(); // 待发送的文件信息列表
    private long lastFileInfoBroadcastTime = 0; // 上次文件信息广播时间
    private boolean fileInfoBroadcastPending = false; // 是否有待处理的文件信息广播

    private AtomicBoolean isScanning = new AtomicBoolean(false);
    private AtomicInteger totalCount = new AtomicInteger(0); // 文件总数计数器
    private AtomicInteger scannedCount = new AtomicInteger(0); // 已扫描文件计数
    private AtomicInteger fixedCount = new AtomicInteger(0); // 已修正文件计数
    private Queue<File> imageFilesQueue = new ConcurrentLinkedQueue<>(); // 使用并发队列代替ArrayList，提高线程安全和性能
    private CopyOnWriteArrayList<ScanFileInfo> imageFileList = new CopyOnWriteArrayList<>(); // 统一存储所有检查分析过的文件信息

    public static class ScanFileInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String filePath;
        private long originalTime;
        private long fixedTime; // 如果为0，表示未修正
        private boolean isFixed;
        private String message; // 修正信息

        public ScanFileInfo(String filePath, long originalTime, long fixedTime, boolean isFixed) {
            this(filePath, originalTime, fixedTime, isFixed, "");
        }

        public ScanFileInfo(String filePath, long originalTime, long fixedTime, boolean isFixed, String message) {
            this.filePath = filePath;
            this.originalTime = originalTime;
            this.fixedTime = fixedTime;
            this.isFixed = isFixed;
            this.message = message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        // Getter方法
        public String getFilePath() {
            return filePath;
        }

        public long getOriginalTime() {
            return originalTime;
        }

        public long getFixedTime() {
            return fixedTime;
        }

        public boolean isFixed() {
            return isFixed;
        }

        // 获取格式化的时间字符串
        public String getOriginalTimeString() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new Date(originalTime));
        }

        public String getFixedTimeString() {
            if (isFixed) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                return sdf.format(new Date(fixedTime));
            }
            return "-";
        }
    }

    // 支持的图片格式
    private static final String[] SUPPORTED_IMAGE_FORMATS = {
            ".jpg", ".jpeg", ".png", ".webp", ".heic"
    };

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                // 初始化扫描状态
                isScanning.set(true);
                scannedCount.set(0);
                fixedCount.set(0);
                imageFilesQueue.clear();
                imageFileList.clear();

                LogUtils.i(TAG, "开始初始化扫描服务");

                // 创建通知渠道
                createNotificationChannel();
                LogUtils.d(TAG, "通知渠道创建完成");

                // 显示前台通知
                showForegroundNotification(getString(R.string.notification_scan_started), 0, 0);
                LogUtils.i(TAG, "前台通知显示完成");

                // 开始扫描
                LogUtils.i(TAG, "开始扫描图片文件");
                startScan();

            } catch (Exception e) {
                LogUtils.e(TAG, "扫描过程中发生错误: " + e.getMessage(), e);
                sendErrorBroadcast(e.getMessage());
            }
        }
    }

    @Override
    public void onCreate() {
        // 初始化日志工具类
        LogUtils.init(this);

        // 创建通知渠道（Android 8.0及以上）
        createNotificationChannel();

        // 显示前台通知
        showForegroundNotification(getString(R.string.notification_scan_started), 0, 0);

        // 创建处理后台任务的线程
        HandlerThread thread = new HandlerThread("ScanServiceThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // 获取该线程的Looper并创建Handler
        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);

        // 获取主线程Handler用于更新通知
        mainHandler = new Handler(Looper.getMainLooper());

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 初始化线程池，使用更安全的配置
        executorService = new ThreadPoolExecutor(
                1, // 核心线程数
                THREAD_POOL_SIZE, // 最大线程数
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100), // 有界队列，避免任务无限积压
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行
        );

        // 初始化内存监控服务
        memoryMonitorService = Executors.newSingleThreadScheduledExecutor();
        startMemoryMonitoring();
    }

    // 启动内存监控
    private void startMemoryMonitoring() {
        memoryMonitorService.scheduleAtFixedRate(() -> {
            try {
                ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);

                // 计算可用内存比例
                float availableMemoryRatio = (float) memoryInfo.availMem / memoryInfo.totalMem;
                boolean wasLowMemoryMode = isLowMemoryMode;
                isLowMemoryMode = availableMemoryRatio < LOW_MEMORY_THRESHOLD;

                // 记录内存状态变化
                if (isLowMemoryMode && !wasLowMemoryMode) {
                    LogUtils.w(TAG, "进入低内存模式，可用内存比例: " + String.format("%.2f%%", availableMemoryRatio * 100));
                    // 触发内存优化
                    optimizeMemoryUsage();
                } else if (!isLowMemoryMode && wasLowMemoryMode) {
                    LogUtils.i(TAG, "退出低内存模式，可用内存比例: " + String.format("%.2f%%", availableMemoryRatio * 100));
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "内存监控失败: " + e.getMessage());
            }
        }, 0, MEMORY_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // 优化内存使用
    private void optimizeMemoryUsage() {
        // 清理不必要的资源
        LogUtils.i(TAG, "执行内存优化...");

        // 手动触发GC
        System.gc();
        Runtime.getRuntime().gc();

        // 限制队列大小，防止内存溢出
        int maxQueueSize = isLowMemoryMode ? 100 : 500;
        while (imageFilesQueue.size() > maxQueueSize) {
            imageFilesQueue.poll(); // 移除最早的文件
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Android 10及以下版本不需要设置前台服务类型
        if (!isScanning.getAndSet(true)) {
            // 重置计数器
            scannedCount.set(0);
            fixedCount.set(0);
            imageFileList.clear();

            // 将开始扫描的任务发送到工作线程
            Message msg = serviceHandler.obtainMessage();
            msg.arg1 = startId;
            serviceHandler.sendMessage(msg);
        }

        // 如果服务被意外终止，重新启动
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 停止扫描
        isScanning.set(false);

        // 关闭线程池
        shutdownExecutors();

        // 退出Looper线程
        if (serviceLooper != null) {
            serviceLooper.quitSafely();
        }

        // 移除所有消息
        if (serviceHandler != null) {
            serviceHandler.removeCallbacksAndMessages(null);
        }

        // 移除主线程的回调
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        // 清理资源
        cleanupResources();

        // 取消前台通知
        stopForeground(true);

        LogUtils.d(TAG, "ScanService已销毁");
    }

    // 关闭所有线程池
    private void shutdownExecutors() {
        // 关闭线程池，避免内存泄漏
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // 立即关闭，中断正在执行的任务
            try {
                // 等待最多5秒让线程池关闭
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    LogUtils.w(TAG, "线程池强制关闭");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LogUtils.w(TAG, "线程池关闭被中断", e);
            }
        }

        // 关闭内存监控服务
        if (memoryMonitorService != null && !memoryMonitorService.isShutdown()) {
            memoryMonitorService.shutdownNow();
            try {
                if (!memoryMonitorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    LogUtils.w(TAG, "内存监控服务强制关闭");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LogUtils.w(TAG, "内存监控服务关闭被中断", e);
            }
        }
    }

    // 清理资源
    private void cleanupResources() {
        // 清理集合
        if (imageFileList != null) {
            imageFileList.clear();
        }

        if (imageFilesQueue != null) {
            imageFilesQueue.clear();
        }

        // 手动触发垃圾回收
        System.gc();
        Runtime.getRuntime().gc();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 扫描任务类
    private class ScanTask implements Runnable {
        private String rootPath;
        private int currentDepth = 0;

        public ScanTask(String rootPath) {
            this.rootPath = rootPath;
        }

        @Override
        public void run() {
            try {
                // 开始扫描
                LogUtils.d(TAG, "开始扫描目录: " + rootPath);
                scanDirectory(new File(rootPath), currentDepth);
                LogUtils.d(TAG, "扫描完成: 扫描了 " + totalCount.get() + " 个文件，分析了 " + scannedCount.get() + " 个文件");

                // 扫描完成后，批量处理所有发现的图片文件
                batchProcessImageFiles();

                // 确保所有待发送的文件信息都已发送
                flushPendingFileInfos();

                if (isScanning.get()) {
                    int finalScannedCount = scannedCount.get();
                    int finalFixedCount = fixedCount.get();

                    // 扫描完成
                    LogUtils.d(TAG, "分析完成: 分析了 " + finalScannedCount + " 个文件，修正了 " + finalFixedCount + " 个文件");

                    // 发送完成广播
                    sendCompletedBroadcast(totalCount.get(), finalScannedCount, finalFixedCount);

                    // 更新完成通知
                    showCompletionNotification();

                    // 延迟关闭服务
                    mainHandler.postDelayed(() -> {
                        stopSelf();
                    }, 2000);
                }

            } catch (Exception e) {
                LogUtils.e(TAG, "扫描任务失败: " + e.getMessage());
                e.printStackTrace();

                // 发送扫描失败广播
                sendErrorBroadcast(e.getMessage());
            } finally {
                // 清理资源
                imageFileList.clear();
                imageFilesQueue.clear();
            }
        }

        // 扫描目录，发现所有图片文件
        private void scanDirectory(File directory, int depth) {
            // 检查是否超过最大深度或服务是否已被停止
            if (depth > MAX_SCAN_DEPTH || !isScanning.get()) {
                return;
            }

            // 检查目录是否存在且可访问
            if (!directory.exists() || !directory.isDirectory() || !directory.canRead()) {
                return;
            }

            // 跳过系统目录和隐藏目录
            if (directory.getName().startsWith(".") ||
                    directory.getName().equals("Android") ||
                    directory.getName().equals("Download") ||
                    directory.getAbsolutePath().contains("\\Android\\data\\") ||
                    directory.getAbsolutePath().contains("\\Android\\obb\\") ||
                    directory.getAbsolutePath().contains("\\.thumbnails")) {
                return;
            }

            // 获取目录下的所有文件和子目录
            File[] files = directory.listFiles();
            if (files == null || files.length == 0) {
                return;
            }

            // 批量处理文件列表，减少重复更新通知
            int batchCount = 0;

            try {
                // 遍历文件和子目录
                for (File file : files) {
                    // 检查服务是否已被停止
                    if (!isScanning.get()) {
                        return;
                    }

                    // 检查内存状态，如果内存不足则暂停扫描
                    if (isLowMemoryMode && batchCount > 0 && batchCount % 20 == 0) {
                        try {
                            Thread.sleep(100); // 暂停一小段时间，让GC有机会运行
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    // 如果是目录，递归扫描
                    if (file.isDirectory()) {
                        scanDirectory(file, depth + 1);
                    } else if (isSupportedImageFile(file)) {
                        // 使用并发队列
                        imageFilesQueue.offer(file);

                        // 增加总文件数计数
                        int currentCount = totalCount.incrementAndGet();
                        batchCount++;

                        // 批量更新通知，减少UI更新频率
                        // if (batchCount % 10 == 0 || currentCount % BATCH_SIZE == 0) {
                        updateProgressNotification(0, 0);
                        // }
                    }
                }
            } catch (SecurityException e) {
                LogUtils.w(TAG, "无法访问目录: " + directory.getAbsolutePath(), e);
            }
        }

        // 批量处理图片文件
        private void batchProcessImageFiles() {
            List<File> batchFiles = new ArrayList<>(BATCH_SIZE);
            File file;
            int batchCount = 0;

            while ((file = imageFilesQueue.poll()) != null && isScanning.get()) {
                batchFiles.add(file);

                // 当批次满了或队列为空时处理批次
                if (batchFiles.size() >= BATCH_SIZE || imageFilesQueue.isEmpty()) {
                    processBatch(batchFiles);
                    batchFiles.clear();
                    batchCount++;

                    // 批次处理后检查内存状态
                    if (isLowMemoryMode) {
                        optimizeMemoryUsage();
                        // 在低内存模式下，处理完一批后短暂暂停
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }

        // 处理单个批次的文件
        private void processBatch(List<File> batchFiles) {
            int batchFixedCount = 0;

            for (File file : batchFiles) {
                if (!isScanning.get())
                    break;

                if (processImageFile(file)) {
                    batchFixedCount++;
                }
                scannedCount.incrementAndGet();
            }

            // 批量更新修复计数，减少原子操作次数
            if (batchFixedCount > 0) {
                fixedCount.addAndGet(batchFixedCount);
            }
            // 更新通知
            updateProgressNotification(scannedCount.get(), fixedCount.get());
        }

        // 修复图片文件
        private boolean processImageFile(File imageFile) {
            try {
                // 限制文件大小，跳过过大的文件
                if (imageFile.length() > 100 * 1024 * 1024) { // 跳过大于100MB的文件
                    LogUtils.w(TAG, "跳过过大的文件: " + imageFile.getAbsolutePath());
                    return false;
                }

                // 获取当前文件的修改时间
                long longCurrentModifiedTime = imageFile.lastModified();
                long longRealModifyDate = 0;
                Date RealModifyDate = null;
                boolean isModified = false;
                boolean isDateFromFileName = false;

                // 读取EXIF信息
                ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
                RealModifyDate = getExifDateTime(exifInterface);

                // 如果不存在EXIF信息，尝试通过文件名称解析出文件创建时间
                if (RealModifyDate == null) {
                    RealModifyDate = getFileNameDateTime(imageFile.getName());
                    isDateFromFileName = true;
                }

                // 如果无法解析出EXIF时间，创建未修正的扫描文件信息
                if (RealModifyDate == null) {
                    LogUtils.d(TAG, "无法解析出EXIF时间，文件时间未修改: " + imageFile.getAbsolutePath());
                    sendFileInfoBroadcast(new ScanFileInfo(
                            imageFile.getAbsolutePath(),
                            longCurrentModifiedTime,
                            longRealModifyDate,
                            false));
                    return false;
                }

                longRealModifyDate = RealModifyDate.getTime();
                // 如果EXIF时间与当前修改时间不同，则更新文件时间
                if (Math.abs(longRealModifyDate - longCurrentModifiedTime) > 1000) { // 允许1秒的误差
                    // 更新文件修改时间
                    imageFile.setLastModified(longRealModifyDate);
                    isModified = true;
                    // @todo 检查文件创建时间，如果晚于修改时间，则设置文件创建时间为修改时间

                    // 文件已修正，日志记录
                    LogUtils.d(TAG, "已修正 " + (isDateFromFileName ? "[文件名]: " : ": ") + " -> "
                            + imageFile.getAbsolutePath() + " -> " + RealModifyDate);

                    // 发送文件信息广播 @todo 考虑移除sendFileInfoBroadcast， 能否通过imageFileList直接更新
                    sendFileInfoBroadcast(new ScanFileInfo(
                            imageFile.getAbsolutePath(),
                            longCurrentModifiedTime,
                            longRealModifyDate,
                            true,
                            " - " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(RealModifyDate)
                                    + (isDateFromFileName ? " 【文件名解析】" : "")));

                    return isModified;
                } else {
                    // 文件不需要修正，创建未修正的扫描文件信息
                    LogUtils.d(TAG, "文件时间正确，无需修正: " + imageFile.getAbsolutePath());
                }

                return isModified;

            } catch (Exception e) {
                // // 发生异常时，创建未修正的扫描文件信息
                // ScanFileInfo scanFileInfo = new ScanFileInfo(
                // imageFile.getAbsolutePath(),
                // imageFile.lastModified(), 0, false);
                // // imageFileList.add(scanFileInfo);

                // // 发送文件信息广播
                // sendFileInfoBroadcast(scanFileInfo);
                LogUtils.e(TAG, "处理文件失败: " + imageFile.getAbsolutePath(), e);
                return false;
            }
        }
    }

    /**
     * 从文件名中提取日期时间信息
     * 支持的格式：
     * 1) IMG_20230101_123045.jpg
     * 2) 20230101_123045.jpg
     * 3) 2023-01-01-12-30-45.jpg
     * 4) 2023.01.01.12.30.45.heic
     * 5) 1748512965775.jpg (Unix时间戳格式)
     * 返回解析出的 Date 对象；若无法解析则返回 null
     */
    private Date getFileNameDateTime(String fileName) {

        // 验证时间戳的合理性（1970-01-01 到 2100-12-31）
        long minTimestamp = 0; // 1970-01-01
        long maxTimestamp = 4102444800000L; // 2100-12-31

        if (fileName == null || fileName.isEmpty())
            return null;

        // 去掉扩展名
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }

        // 匹配 2023-01-01-12-30-45 或 2023.01.01.12.30.45
        java.util.regex.Pattern p2 = java.util.regex.Pattern
                .compile(
                        "(\\d{4})[\\.\\-](\\d{2})[\\.\\-](\\d{2})[\\.\\-](\\d{2})[\\.\\-](\\d{2})[\\.\\-](\\d{2})");
        java.util.regex.Matcher m2 = p2.matcher(fileName);
        if (m2.find()) {
            return parseExifDateTime(
                    new StringBuilder()
                            .append(m2.group(1)).append(':') // yyyy
                            .append(m2.group(2)).append(':') // MM
                            .append(m2.group(3)).append(' ') // dd
                            .append(m2.group(4)).append(':') // HH
                            .append(m2.group(5)).append(':') // mm
                            .append(m2.group(6)) // ss
                            .toString());
        }

        // 匹配 .2023_02_17 下午9_30 Office Lens (16) 格式
        java.util.regex.Pattern p4 = java.util.regex.Pattern
                .compile("\\.(\\d{4})_(\\d{2})_(\\d{2})\\s+([上下午]+)(\\d+)_(\\d+)");
        java.util.regex.Matcher m4 = p4.matcher(fileName);
        if (m4.find()) {
            int hour = Integer.parseInt(m4.group(5));
            // 处理上午/下午
            if (m4.group(4).contains("下午") && hour < 12) {
                hour += 12;
            } else if (m4.group(4).contains("上午") && hour == 12) {
                hour = 0;
            }
            
            return parseExifDateTime(
                    new StringBuilder()
                            .append(m4.group(1)).append(':') // yyyy
                            .append(m4.group(2)).append(':') // MM
                            .append(m4.group(3)).append(' ') // dd
                            .append(String.format("%02d", hour)).append(':') // HH
                            .append(m4.group(6)).append(':') // mm
                            .append("00") // ss
                            .toString());
        }

        // 匹配Unix时间戳格式 (10位秒级或13位毫秒级)，支持前后带有非数字字符串
        java.util.regex.Pattern timestampPattern = java.util.regex.Pattern.compile("[^\\d]*(\\d{10}|\\d{13})[^\\d]*");
        java.util.regex.Matcher timestampMatcher = timestampPattern.matcher(fileName);
        if (timestampMatcher.find()) {
            try {
                String timestampStr = timestampMatcher.group(1);
                long timestamp;
                // 判断是秒级还是毫秒级时间戳
                if (timestampStr.length() == 10) {
                    // 秒级时间戳，转换为毫秒
                    timestamp = Long.parseLong(timestampStr) * 1000;
                } else {
                    // 毫秒级时间戳
                    timestamp = Long.parseLong(timestampStr);
                }

                // 验证时间戳的合理性（1970-01-01 到 2100-12-31）
                if (timestamp >= minTimestamp && timestamp <= maxTimestamp) {
                    return new Date(timestamp);
                }
            } catch (NumberFormatException e) {
                // 解析失败，继续尝试其他格式
            }
        }

        // 匹配 ***20230101_123045*** 或 ***20230101*** 格式，支持任意前后缀，时间部分可选
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("[^\\d]*(\\d{8})(?:_(\\d{6}))?[^\\d]*",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m1 = p1.matcher(fileName);
        if (m1.find()) {
            String date = m1.group(1); // 20230101
            String time = m1.group(2); // 123045 或 null（如果时间部分不存在）

            StringBuilder dateTimeBuilder = new StringBuilder()
                    .append(date, 0, 4).append(':')
                    .append(date, 4, 6).append(':')
                    .append(date, 6, 8).append(' ');

            // 如果时间部分存在，则添加时间信息，否则默认为 00:00:00
            if (time != null) {
                dateTimeBuilder.append(time, 0, 2).append(':')
                        .append(time, 2, 4).append(':')
                        .append(time, 4, 6);
            } else {
                dateTimeBuilder.append("00:00:00");
            }
            
            // 验证时间戳的合理性（1970-01-01 到 2100-12-31）
            long timestamp = parseExifDateTime(dateTimeBuilder.toString()).getTime();
            if (timestamp >= minTimestamp && timestamp <= maxTimestamp) {
                return new Date(timestamp);
            }

        }

        // 匹配 灵活的日期时间格式，支持多种分隔符，时间、秒和毫秒都是可选的
        // 支持格式示例：2022-06-25_12.13.07.326、2022/06/25 12:13、2022_06_25-12.13.07等
        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(
                "[^\\d]*?(\\d{4})[\\-\\/_\\.](\\d{2})[\\-\\/_\\.](\\d{2})[^\\d]*?(?:(\\d{2})[\\-\\:_\\.](\\d{2})(?:[\\-\\:_\\.](\\d{2})(?:[\\-\\:_\\.](\\d{1,3}))?)?)?[^\\d]*?");
        java.util.regex.Matcher m3 = p3.matcher(fileName);
        if (m3.find()) {
            // 构建日期时间字符串
            StringBuilder dateTimeStrBuilder = new StringBuilder()
                    .append(m3.group(1)).append(':') // yyyy
                    .append(m3.group(2)).append(':') // MM
                    .append(m3.group(3)).append(' '); // dd

            // 时间部分是可选的
            String hourGroup = m3.group(4);
            String minuteGroup = m3.group(5);
            String secondGroup = m3.group(6);
            String millisecondGroup = m3.group(7);

            // 如果有时间部分（小时和分钟）
            if (hourGroup != null && minuteGroup != null) {
                dateTimeStrBuilder
                        .append(hourGroup).append(':') // HH
                        .append(minuteGroup); // mm

                // 秒部分是可选的
                if (secondGroup != null) {
                    dateTimeStrBuilder.append(':').append(secondGroup); // ss
                } else {
                    dateTimeStrBuilder.append(':').append("00"); // 默认秒为00
                }
            } else {
                // 如果没有时间部分，添加默认时间 00:00:00
                dateTimeStrBuilder.append("00:00:00");
            }

            String dateTimeStr = dateTimeStrBuilder.toString();
            Date date = parseExifDateTime(dateTimeStr);

            if (date != null && millisecondGroup != null) {
                // 添加毫秒（处理1-3位毫秒）
                try {
                    // 确保毫秒是3位数
                    String paddedMillis = String.format("%3s", millisecondGroup).replace(' ', '0');
                    int milliseconds = Integer.parseInt(paddedMillis);
                    return new Date(date.getTime() + milliseconds);
                } catch (NumberFormatException e) {
                    // 忽略毫秒部分，返回不带毫秒的时间
                }
            }

            // 验证时间戳的合理性（1970-01-01 到 2100-12-31）
            long timestamp = date.getTime();
            if (timestamp >= minTimestamp && timestamp <= maxTimestamp) {
                return date;
            }
        }

        return null;
    }

    private void startScan() {
        // 创建并启动扫描任务
        ScanTask scanTask = new ScanTask(Environment.getExternalStorageDirectory().getAbsolutePath());
        executorService.execute(scanTask);
    }

    private boolean isSupportedImageFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        String fileName = file.getName().toLowerCase();
        for (String format : SUPPORTED_IMAGE_FORMATS) {
            if (fileName.endsWith(format)) {
                return true;
            }
        }
        return false;
    }

    private Date getExifDateTime(ExifInterface exifInterface) {
        // 尝试获取不同的日期时间标签
        String dateString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
        if (dateString == null || dateString.isEmpty()) {
            dateString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
        }
        if (dateString == null || dateString.isEmpty()) {
            dateString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
        }
        return dateString != null && !dateString.isEmpty() ? parseExifDateTime(dateString) : null;
    }

    private Date parseExifDateTime(String dateString) {
        try {
            // EXIF日期格式: "2023:01:01 12:30:45"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());
            return sdf.parse(dateString);
        } catch (ParseException e) {
            LogUtils.w(TAG, "解析日期时间失败: " + dateString, e);
            return null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "扫描服务",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("图片扫描和时间修正服务");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 显示前台通知，展示扫描进度和修复状态
     *
     * @param content  通知内容
     * @param progress 当前进度
     * @param total    总任务数
     */
    private void showForegroundNotification(String content, int progress, int total) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setProgress(total, progress, total == 0)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateProgressNotification(int scanned, int fixed) {
        // 使用延迟更新机制，避免频繁更新
        updateNotificationDelayed(scanned, fixed);

        // 发送进度广播，通知UI更新
        Intent intent = new Intent(ACTION_SCAN_PROGRESS);
        intent.putExtra(EXTRA_TOTAL_FILES, totalCount.get());
        intent.putExtra(EXTRA_SCANNED_COUNT, scanned);
        intent.putExtra(EXTRA_FIXED_COUNT, fixed);
        // 设置包名以避免UnsafeImplicitIntentLaunch错误
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

    }

    /**
     * 延迟更新通知，避免频繁更新导致UI卡顿
     */
    private void updateNotificationDelayed(final int scanned, final int fixed) {
        // 检查是否有足够的变化来更新通知，避免微小变化导致的频繁更新
        if (Math.abs(scanned - lastScannedCount) < 10 && Math.abs(fixed - lastFixedCount) < 5) {
            return;
        }

        // 检查是否已经有待处理的更新
        if (notificationUpdatePending) {
            return;
        }

        // 检查是否满足最小更新间隔
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationUpdateTime < MIN_NOTIFICATION_INTERVAL_MS) {
            // 如果不满足间隔，安排延迟更新
            notificationUpdatePending = true;
            mainHandler.postDelayed(() -> {
                performNotificationUpdate(scanned, fixed);
                notificationUpdatePending = false;
            }, MIN_NOTIFICATION_INTERVAL_MS - (currentTime - lastNotificationUpdateTime));
            return;
        }

        // 立即更新通知
        performNotificationUpdate(scanned, fixed);
    }

    /**
     * 执行实际的通知更新
     */
    private void performNotificationUpdate(int scanned, int fixed) {
        try {
            String content = getString(R.string.notification_scan_progress, scanned, fixed);
            showForegroundNotification(content, scanned, totalCount.get());

            // 更新最后更新时间和计数
            lastNotificationUpdateTime = System.currentTimeMillis();
            lastScannedCount = scanned;
            lastFixedCount = fixed;
        } catch (Exception e) {
            LogUtils.e(TAG, "更新通知失败", e);
        }
    }

    private void showCompletionNotification() {
        // Android 13及以上版本需要检查POST_NOTIFICATIONS权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 没有通知权限，不发送通知
                LogUtils.d(TAG, "No POST_NOTIFICATIONS permission, skipping notification");
                return;
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_scan_completed))
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    // 添加到待发送列表
    private void sendFileInfoBroadcast(ScanFileInfo fileInfo) {
        synchronized (pendingFileInfos) {
            pendingFileInfos.add(fileInfo);

            // 检查是否需要立即发送（达到批量大小）
            if (pendingFileInfos.size() >= FILE_INFO_BATCH_SIZE) {
                flushPendingFileInfos();
                return;
            }
        }

        // 检查是否需要延迟发送
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFileInfoBroadcastTime >= FILE_INFO_BROADCAST_INTERVAL_MS
                && !fileInfoBroadcastPending) {
            fileInfoBroadcastPending = true;
            mainHandler.postDelayed(() -> {
                flushPendingFileInfos();
                fileInfoBroadcastPending = false;
            }, FILE_INFO_BROADCAST_INTERVAL_MS);
        }
    }

    private void flushPendingFileInfos() {
        List<ScanFileInfo> filesToSend;
        synchronized (pendingFileInfos) {
            if (pendingFileInfos.isEmpty()) {
                return;
            }

            // 创建一个副本用于发送
            filesToSend = new ArrayList<>(pendingFileInfos);
            pendingFileInfos.clear();
        }

        // 发送批处理广播
        Intent intent = new Intent(ACTION_FILE_INFO_UPDATE);
        // 将List转换为ArrayList以确保可序列化
        intent.putExtra(EXTRA_SCANNED_FILES_LIST, new ArrayList<>(filesToSend));
        // 设置包名以避免UnsafeImplicitIntentLaunch错误
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        lastFileInfoBroadcastTime = System.currentTimeMillis();
    }

    private void sendCompletedBroadcast(int total, int scanned, int fixed) {
        Intent intent = new Intent(ACTION_SCAN_COMPLETED);
        intent.putExtra(EXTRA_TOTAL_FILES, total);
        intent.putExtra(EXTRA_SCANNED_COUNT, scanned);
        intent.putExtra(EXTRA_FIXED_COUNT, fixed);

        // 将统一的文件信息列表作为可序列化对象传递
        intent.putExtra(EXTRA_SCANNED_FILES_LIST, new ArrayList<>(imageFileList));

        // 设置包名以避免UnsafeImplicitIntentLaunch错误
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void sendErrorBroadcast(String errorMessage) {
        Intent intent = new Intent(ACTION_SCAN_ERROR);
        intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage);
        // 设置包名以避免UnsafeImplicitIntentLaunch错误
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

}