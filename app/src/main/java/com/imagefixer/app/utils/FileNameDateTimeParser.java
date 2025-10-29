package com.imagefixer.app.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.imagefixer.app.utils.LogUtils;

/**
 * 文件名字符串日期时间解析器
 * <p>
 * 用于从文件名中提取日期时间信息，支持多种日期时间格式。
 * </p>
 */
public class FileNameDateTimeParser {
    private static final String TAG = "FileNameDateTimeParser";

    /**
     * 从文件名中提取日期时间信息
     * <p>
     * 支持的格式包括：
     * 1) 20230101_123045 - 紧凑格式，日期+时间
     * 2) 2023-01-01-12-30-45 - 带分隔符的完整日期时间格式
     * 3) 20230101 - 仅日期格式
     * 4) 2023.01.01.12.30.45 - 带点分隔符的格式
     * 5) 2022/06/25 12:13 - 灵活分隔符格式
     * 6) .2023_02_17 下午9_30 Office Lens (16) - 中文上午/下午格式
     * 7) 1748512965775.jpg - Unix时间戳格式(10位秒级或13位毫秒级)
     * </p>
     * 
     * @param fileName 文件名（包含扩展名）
     * @return 解析出的 Date 对象；若无法解析则返回 null
     */
    public Date getFileNameDateTime(String fileName) {
        // 验证时间戳的合理性（1970-01-01 到 2100-12-31）
        long minTimestamp = 0; // 1970-01-01
        long maxTimestamp = System.currentTimeMillis(); // 当前时间戳

        if (fileName == null || fileName.isEmpty())
            return null;

        // 去掉扩展名
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
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

        // 匹配 ***20230101_123045*** 或 ***20230101*** 或 ***20230101126040***
        // 格式，支持任意前后缀，时间部分可选
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("[^\\d]*(\\d{8})(?:[_\\s]?+(\\d{6}))?[^\\d]*",
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

            // 验证时间戳的合理性（1970-01-01 到 ）
            Date parsedDate = parseExifDateTime(dateTimeBuilder.toString());
            if (parsedDate != null) {
                long timestamp = parsedDate.getTime();
                if (timestamp >= minTimestamp && timestamp <= maxTimestamp) {
                    return parsedDate;
                }
            }
        }

        // 匹配 灵活的日期时间格式，支持多种分隔符，时间、秒和毫秒都是可选的
        // 支持格式示例：2022-06-25_12.13.07.326、2022/06/25 12:13、2022_06_25-12.13.07等
        // 支持分隔符：- / _ . 空格
        // 使用更简洁可读的正则表达式格式
        // "(\\d{4})[-\\/\\:_\\.\\s]+(\\d{2})[-\\/\\:_\\.\\s]+(\\d{2})\\s*?(?:(\\d{2})[-\\/\\:_\\.\\s]+(\\d{2})(?:[-\\/\\:_\\.\\s]+(\\d{2})(?:[-\\/\\:_\\.\\s]+(\\d{1,3}))?)?)?");
        String dateSeparator = "[-\\/\\:_\\.\\s]";
        String p3Pattern = String.format(
                "^.*?(\\d{4})%s(\\d{2})%s(\\d{2})%s+?(?:(\\d{2})%s(\\d{2})(?:%s(\\d{2})(?:%s(\\d{1,3}))?)?)?.*$",
                dateSeparator, dateSeparator, dateSeparator, dateSeparator, dateSeparator, dateSeparator);
        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(p3Pattern);
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
            if (date != null) {
                long timestamp = date.getTime();
                if (timestamp >= minTimestamp && timestamp <= maxTimestamp) {
                    return date;
                }
            }
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
                LogUtils.w(TAG, "解析Unix时间戳失败: " + timestampMatcher.group(1), e);
            }
        }

        // 匹配 2023-01-01、2023-01或2023格式，分隔符支持-、/、.、_和空格
        java.util.regex.Pattern p2 = java.util.regex.Pattern
                .compile(
                        "(\\d{4})([-\\/\\._\\s])?(\\d{2})?([-\\/\\._\\s])?(\\d{2})?");
        java.util.regex.Matcher m2 = p2.matcher(fileName);
        if (m2.find()) {
            StringBuilder dateTimeBuilder = new StringBuilder()
                    .append(m2.group(1)).append(':'); // yyyy

            // 如果有月份，添加月份，否则默认为01
            if (m2.group(3) != null) {
                dateTimeBuilder.append(m2.group(3)).append(':');
            } else {
                dateTimeBuilder.append("01:");
            }

            // 如果有日期，添加日期，否则默认为01
            if (m2.group(5) != null) {
                dateTimeBuilder.append(m2.group(5)).append(' ');
            } else {
                dateTimeBuilder.append("01 ");
            }

            // 时间部分默认为00:00:00
            dateTimeBuilder.append("00:00:00");

            // 验证时间戳的合理性（1970-01-01 到 ）
            Date parsedDate = parseExifDateTime(dateTimeBuilder.toString());
            if (parsedDate != null) {
                long timestamp = parsedDate.getTime();
                if (timestamp >= minTimestamp && timestamp <= maxTimestamp) {
                    return parsedDate;
                }
            }

        }

        return null;
    }

    /**
     * 解析EXIF格式的日期时间字符串
     * <p>
     * EXIF日期格式: "2023:01:01 12:30:45"
     * </p>
     * 
     * @param dateString EXIF格式的日期时间字符串
     * @return 解析出的 Date 对象；若解析失败则返回 null
     */
    public Date parseExifDateTime(String dateString) {
        try {
            // EXIF日期格式: "2023:01:01 12:30:45"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());
            return sdf.parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }
}