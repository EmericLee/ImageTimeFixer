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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.imagefixer.app.utils.LogUtils;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private Button buttonScan;
    private Button buttonStop;
    private TextView textViewStatus;
    private TextView textViewTotalCount;
    private TextView textViewScannedCount;
    private TextView textViewFixedCount;
    private ProgressBar progressBar;
    private Button buttonLog; // 用于显示日志的按钮

    private StringBuilder strLogBuilder = new StringBuilder(); // 用于存储日志内容

    private TextView textViewFileList;
    private ScrollView scrollViewFiles;

    private boolean isScanning = false;

    private BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ScanService.ACTION_SCAN_PROGRESS.equals(action)) {
                int totalCount = intent.getIntExtra(ScanService.EXTRA_TOTAL_FILES, 0);
                int scannedCount = intent.getIntExtra(ScanService.EXTRA_SCANNED_COUNT, 0);
                int fixedCount = intent.getIntExtra(ScanService.EXTRA_FIXED_COUNT, 0);
                updateScanProgress(totalCount, scannedCount, fixedCount);
            } else if (ScanService.ACTION_SCAN_COMPLETED.equals(action)) {
                int totalCount = intent.getIntExtra(ScanService.EXTRA_TOTAL_FILES, 0);
                int scannedCount = intent.getIntExtra(ScanService.EXTRA_SCANNED_COUNT, 0);
                int fixedCount = intent.getIntExtra(ScanService.EXTRA_FIXED_COUNT, 0);
                scanCompleted(totalCount, scannedCount, fixedCount);
            } else if (ScanService.ACTION_SCAN_ERROR.equals(action)) {
                String errorMessage = intent.getStringExtra(ScanService.EXTRA_ERROR_MESSAGE);
                scanError(errorMessage);
            } else if (ScanService.ACTION_FILE_INFO_UPDATE.equals(action)) {
                // 处理文件信息更新广播 - 批量处理
                ArrayList<ScanService.ScanFileInfo> fileInfos = (ArrayList<ScanService.ScanFileInfo>) intent
                        .getSerializableExtra(ScanService.EXTRA_SCANNED_FILES_LIST);
                if (fileInfos != null && !fileInfos.isEmpty()) {
                    updateFileListBatch(fileInfos);
                }
            } else if (LogUtils.ACTION_LOG_MESSAGE.equals(action)) {
                // 处理日志消息广播
                String message = intent.getStringExtra(LogUtils.EXTRA_LOG_MESSAGE);
                String level = intent.getStringExtra(LogUtils.EXTRA_LOG_LEVEL);
                updateLogView(message, level);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化日志工具类
        LogUtils.init(this);

        initViews();
        setListeners();

        // 检查并请求权限
        checkPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注册广播接收器，添加RECEIVER_NOT_EXPORTED标志以适应Android 12+的要求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_SCAN_PROGRESS), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_SCAN_COMPLETED), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_SCAN_ERROR), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_FILE_INFO_UPDATE), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(scanReceiver, new IntentFilter(LogUtils.ACTION_LOG_MESSAGE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_SCAN_PROGRESS));
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_SCAN_COMPLETED));
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_SCAN_ERROR));
            registerReceiver(scanReceiver, new IntentFilter(ScanService.ACTION_FILE_INFO_UPDATE));
            registerReceiver(scanReceiver, new IntentFilter(LogUtils.ACTION_LOG_MESSAGE));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 注销广播接收器
        unregisterReceiver(scanReceiver);
    }

    private void initViews() {
        buttonScan = findViewById(R.id.button_scan);
        buttonStop = findViewById(R.id.button_stop);
        textViewStatus = findViewById(R.id.textView_status);
        textViewTotalCount = findViewById(R.id.textView_total_count);
        textViewScannedCount = findViewById(R.id.textView_scanned_count);
        textViewFixedCount = findViewById(R.id.textView_fixed_count);
        progressBar = findViewById(R.id.progressBar);
        buttonLog = findViewById(R.id.button_log); // 初始化日志按钮
        
        scrollViewFiles = findViewById(R.id.scrollView_files);
        textViewFileList = findViewById(R.id.textView_file_list);

        // 清空显示内容
        textViewFileList.setText("");
        strLogBuilder.setLength(0); // 清空日志

        // 初始化计数器显示
        textViewTotalCount.setText(getString(R.string.text_total_count, 0));
        textViewScannedCount.setText(getString(R.string.text_scanned_count, 0));
        textViewFixedCount.setText(getString(R.string.text_fixed_count, 0));
    }

    private void setListeners() {
        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission()) {
                    startScan();
                }
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScan();
            }
        });
        
        // 为日志滚动容器添加点击放大功能
        buttonLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogInFullScreen();
            }
        });
    }
    
    /**
     * 在全屏对话框中显示日志内容
     */
    private void showLogInFullScreen() {
        // 获取当前日志内容
        String logContent = strLogBuilder.toString();
        
        // 创建一个新的ScrollView和TextView用于全屏显示
        ScrollView fullScreenScrollView = new ScrollView(this);
        TextView fullScreenTextView = new TextView(this);
        
        // 设置文本视图的属性
        fullScreenTextView.setText(logContent); // 直接设置字符串内容
        fullScreenTextView.setTextSize(14); // 设置合适的字体大小
        // fullScreenTextView.setTextColor(textViewLog.getCurrentTextColor());
        fullScreenTextView.setPadding(16, 16, 16, 16); // 设置内边距
        fullScreenTextView.setVerticalScrollBarEnabled(true);
        fullScreenTextView.setHorizontallyScrolling(false); // 禁用水平滚动，允许文本自动换行
        fullScreenTextView.setSingleLine(false); // 允许多行显示
        fullScreenTextView.setEllipsize(null); // 不截断文本
        fullScreenTextView.setMovementMethod(null); // 移除任何可能干扰的移动方法
        
        // 设置背景色
        // fullScreenScrollView.setBackgroundColor(getResources().getColor(android.R.color.background_dark));
        
        // 将文本视图添加到滚动视图中
        fullScreenScrollView.addView(fullScreenTextView);
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("日志详情")
               .setView(fullScreenScrollView)
               .setPositiveButton("关闭", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {
                       dialog.dismiss();
                   }
               });
        
        // 创建并显示对话框
        AlertDialog dialog = builder.create();
        
        // 设置对话框全屏显示
        dialog.show();
        
        // 获取对话框的窗口并设置其布局参数，使其几乎充满屏幕
        dialog.getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.95), // 宽度为屏幕的95%
            (int) (getResources().getDisplayMetrics().heightPixels * 0.9) // 高度为屏幕的90%
        );
        
        // 滚动到最新内容
        fullScreenScrollView.post(new Runnable() {
            @Override
            public void run() {
                fullScreenScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Android 11及以上需要特殊的存储管理权限
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
                showPermissionDialog();
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE },
                        PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_permission)
                .setMessage(R.string.message_permission)
                .setPositiveButton(R.string.btn_permission_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.btn_permission_deny, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要存储权限才能继续", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startScan() {
        isScanning = true;
        updateUIState(true);

        // 启动扫描服务
        Intent intent = new Intent(this, ScanService.class);
        startService(intent);
    }

    private void stopScan() {
        isScanning = false;
        updateUIState(false);

        // 停止扫描服务
        Intent intent = new Intent(this, ScanService.class);
        stopService(intent);

        textViewStatus.setText(R.string.status_idle);
    }

    private void updateScanProgress(int totalCount, int scannedCount, int fixedCount) {
        textViewTotalCount.setText(getString(R.string.text_total_count, totalCount));
        textViewScannedCount.setText(getString(R.string.text_scanned_count, scannedCount));
        textViewFixedCount.setText(getString(R.string.text_fixed_count, fixedCount));
        progressBar.setMax(totalCount);
        progressBar.setProgress(scannedCount);
    }

    private void scanCompleted(int totalCount, int scannedCount, int fixedCount) {
        isScanning = false;
        updateUIState(false);

        textViewStatus.setText(R.string.status_completed);
        Toast.makeText(this, getString(R.string.text_summary, totalCount, scannedCount, fixedCount), Toast.LENGTH_LONG)
                .show();
    }

    private void scanError(String errorMessage) {
        isScanning = false;
        updateUIState(false);

        textViewStatus.setText(R.string.status_error);
        Toast.makeText(this, "扫描错误: " + errorMessage, Toast.LENGTH_LONG).show();
    }

    private void updateUIState(boolean scanning) {
        if (scanning) {
            textViewStatus.setText(R.string.status_scanning);
            // progressBar.setVisibility(View.VISIBLE);
            // progressBar.setIndeterminate(true);
            buttonScan.setVisibility(View.GONE);
            buttonStop.setVisibility(View.VISIBLE);
            // scrollViewFiles.setVisibility(View.VISIBLE);
        } else {
            // progressBar.setVisibility(View.GONE);
            buttonScan.setVisibility(View.VISIBLE);
            buttonStop.setVisibility(View.GONE);
        }
    }

    /**
     * 更新日志视图显示
     * 
     * @param message 日志消息
     * @param level   日志级别
     */
    private void updateLogView(String message, String level) {


        // 为不同级别的日志添加不同的前缀和颜色
        String prefix = "";
        switch (level) {
            case "DEBUG":
                prefix = "[D] ";
                break;
            case "INFO":
                prefix = "[I] ";
                break;
            case "WARNING":
                prefix = "[W] ";
                break;
            case "ERROR":
                prefix = "[E] ";
                break;
            default:
                prefix = "[?] ";
        }

        // 格式化日志消息，添加时间戳
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        String timestamp = sdf.format(new Date());
        String formattedMessage = timestamp + " " + prefix + message + "\n";

        // 添加到日志视图
        strLogBuilder.append(formattedMessage);
        // textViewLog.append(formattedMessage);

        // // 自动滚动到最新内容
        // textViewLog.post(new Runnable() {
        //     @Override
        //     public void run() {
        //         // 确保在UI线程执行滚动操作
        //         if (scrollViewLog != null) {
        //             scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);
        //         }
        //     }
        // });

    }

    private void updateFileListBatch(List<ScanService.ScanFileInfo> fileInfos) {
        if (fileInfos == null || fileInfos.isEmpty()) {
            return;
        }

        // 使用StringBuilder预构建所有新内容，然后一次性append到TextView
        StringBuilder sb = new StringBuilder();

        strLogBuilder.append("updateFileListBatch: " + fileInfos.size() + " files");

        for (ScanService.ScanFileInfo fileInfo : fileInfos) {
            // 获取文件名（从路径中提取）
            String fileName = fileInfo.getFilePath().substring(fileInfo.getFilePath().lastIndexOf('/') + 1);

            // 根据是否修正文件显示不同内容

            sb.append(fileInfo.isFixed() ? "+ " : "x ") // 已修正标记
                    .append(fileName);
            // .append(" - 已修正 (原时间: ")
            // .append(fileInfo.getOriginalTimeString())
            if (fileInfo.getMessage() != null && !fileInfo.getMessage().isEmpty()) {
                sb.append("\n          " + fileInfo.getMessage());
            }
            sb.append("\n");
        }

        // 使用append方法添加新内容，避免每次都重新创建完整文本
        textViewFileList.append(sb.toString());

        // 自动滚动到最新内容
        scrollViewFiles.post(new Runnable() {
            @Override
            public void run() {
                scrollViewFiles.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }
}