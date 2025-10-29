package com.imagefixer.app.utils;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FileNameDateTimeParser的单元测试类
 * 使用对照表方式管理测试用例，方便扩展和维护
 */
public class FileNameDateTimeParserTest {

    private FileNameDateTimeParser parser;
    private SimpleDateFormat dateFormat;

    // 日期时间格式对照表 - 输入文件名 -> 预期格式化日期
    private static final Map<String, String> DATE_TIME_TEST_CASES = new HashMap<>();
    // 特殊测试用例 - 需要特殊断言的测试场景
    private static final Map<String, TestCaseInfo> SPECIAL_TEST_CASES = new HashMap<>();
    // 空值/无效测试用例
    private static final List<String> INVALID_INPUTS = Arrays.asList(null, "", "random_file_name.jpg", "abc123def.jpg");
    // 超出范围的时间戳
    private static final String OUT_OF_RANGE_TIMESTAMP = "99999999999999.jpg";

    // 静态初始化测试用例
    static {
        // 基础格式测试用例
        DATE_TIME_TEST_CASES.put("IMG_20230101_123045.jpg", "2023-01-01 12:30:45");
        DATE_TIME_TEST_CASES.put("photo_2023-01-01-12-30-45.png", "2023-01-01 12:30:45");
        DATE_TIME_TEST_CASES.put("document_20230101.pdf", "2023-01-01 00:00:00");
        DATE_TIME_TEST_CASES.put("image_2023.01.01.12.30.45.bmp", "2023-01-01 12:30:45");
        DATE_TIME_TEST_CASES.put("image_20230101123045.bmp", "2023-01-01 12:30:45");
        DATE_TIME_TEST_CASES.put("image_20230101123045_.bmp", "2023-01-01 12:30:45");
        // DATE_TIME_TEST_CASES.put("2023:01:01 12:30:45", "2023-01-01 12:30:45");
        // 空格分隔符测试用例
        DATE_TIME_TEST_CASES.put("report 2023 01 03.pdf", "2023-01-03 00:00:00");
        DATE_TIME_TEST_CASES.put("A2023.01.02 13.45.30.jpg", "2023-01-02 13:45:30");
        DATE_TIME_TEST_CASES.put("image 2023.01.02.jpg", "2023-01-02 00:00:00");
        DATE_TIME_TEST_CASES.put("image 2023.jpg", "2023-01-01 00:00:00");
        DATE_TIME_TEST_CASES.put("file_2023 01 04 14:30.jpg", "2023-01-04 14:30:00");

        // 中文上午/下午格式
        DATE_TIME_TEST_CASES.put(".2023_02_17 下午9_30 Office Lens (16).jpg", "2023-02-17 21:30:00");
        DATE_TIME_TEST_CASES.put(".2023_02_17 上午9_30 Office Lens (16).jpg", "2023-02-17 09:30:00");
        DATE_TIME_TEST_CASES.put(".2023_02_17 上午12_30 Office Lens (16).jpg", "2023-02-17 00:30:00");

        // // 特殊格式测试用例（需要特殊断言）
        // SPECIAL_TEST_CASES.put("2022/06/25 12:13.jpg", new TestCaseInfo(2022,
        // Calendar.JUNE, null));
        // SPECIAL_TEST_CASES.put("IMG_20230101_123045_EDITED.jpg", new
        // TestCaseInfo(2023, Calendar.JANUARY, 1));
        // SPECIAL_TEST_CASES.put("2023-01-01.jpg", new TestCaseInfo(2023,
        // Calendar.JANUARY, null));
    }

    // 测试用例信息类，用于存储特殊测试场景
    private static class TestCaseInfo {
        private Integer expectedYear;
        private Integer expectedMonth; // Calendar中的月份（0-11）
        private Integer expectedDay;

        public TestCaseInfo(Integer year, Integer month, Integer day) {
            this.expectedYear = year;
            this.expectedMonth = month;
            this.expectedDay = day;
        }

        public void assertDate(Date date) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            if (expectedYear != null) {
                assertEquals("年份应正确", expectedYear.intValue(), cal.get(Calendar.YEAR));
            }
            if (expectedMonth != null) {
                assertEquals("月份应正确", expectedMonth.intValue(), cal.get(Calendar.MONTH));
            }
            if (expectedDay != null) {
                assertEquals("日期应正确", expectedDay.intValue(), cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    }

    @Before
    public void setUp() {
        parser = new FileNameDateTimeParser();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    /**
     * 使用对照表批量测试标准日期时间格式解析
     * 适用于需要精确比较完整日期时间的测试用例
     */
    @Test
    public void testGetFileNameDateTime_StandardFormats() {
        for (Map.Entry<String, String> entry : DATE_TIME_TEST_CASES.entrySet()) {
            String fileName = entry.getKey();
            String expectedFormattedDate = entry.getValue();

            // 打印测试信息，便于调试
            System.out.println("Testing format: " + fileName + " -> " + expectedFormattedDate);

            Date date = parser.getFileNameDateTime(fileName);
            assertNotNull("文件名: " + fileName + " 应该能解析出日期", date);
            assertEquals("文件名: " + fileName + " 的解析结果应该正确",
                    expectedFormattedDate, dateFormat.format(date));
        }
    }

    /**
     * 批量测试无效文件名解析
     */
    @Test
    public void testGetFileNameDateTime_InvalidFileName() {
        for (String input : INVALID_INPUTS) {
            // 打印测试信息，便于调试
            System.out.println("Testing invalid input: " + (input == null ? "null" : input));

            Date date = parser.getFileNameDateTime(input);
            assertNull("输入: " + (input == null ? "null" : input) + " 应该返回null", date);
        }
    }

    /**
     * 便捷添加新测试用例的辅助方法
     * 可以在开发过程中临时使用，不需要修改测试类结构
     * 使用示例：
     * addAndTestNewFormat("new_file_name_20240101.jpg", "2024-01-01 00:00:00");
     * addAndTestNewSpecialFormat("new_format_2024_02_01.jpg", 2024,
     * Calendar.FEBRUARY, null);
     */
    private void addAndTestNewFormat(String fileName, String expectedFormattedDate) {
        Date date = parser.getFileNameDateTime(fileName);
        assertNotNull("新增测试用例: " + fileName + " 应该能解析出日期", date);
        assertEquals("新增测试用例: " + fileName + " 的解析结果应该正确",
                expectedFormattedDate, dateFormat.format(date));
    }

    /**
     * 测试边界时间戳
     */
    @Test
    public void testGetFileNameDateTime_BoundaryTimestamp() {
        // 测试超出合理范围的时间戳
        Date dateFuture = parser.getFileNameDateTime(OUT_OF_RANGE_TIMESTAMP);
        assertNull("超出合理范围的时间戳应该返回null", dateFuture);
    }

    // /**
    // * 测试特殊格式的日期解析
    // * 适用于只需检查部分日期字段（年、月、日）的测试用例
    // */
    // @Test
    // public void testGetFileNameDateTime_SpecialFormats() {
    // for (Map.Entry<String, TestCaseInfo> entry : SPECIAL_TEST_CASES.entrySet()) {
    // String fileName = entry.getKey();
    // TestCaseInfo testInfo = entry.getValue();

    // // 打印测试信息，便于调试
    // System.out.println("Testing special format: " + fileName);

    // Date date = parser.getFileNameDateTime(fileName);
    // assertNotNull("文件名: " + fileName + " 应该能解析出日期", date);
    // testInfo.assertDate(date);
    // }
    // }

    // /**
    // * 测试Unix时间戳格式解析
    // */
    // @Test
    // public void testGetFileNameDateTime_TimestampFormat() {
    // // 测试13位毫秒级时间戳
    // Date date13 = parser.getFileNameDateTime("1748512965775.jpg");
    // assertNotNull("应该能解析13位毫秒级时间戳", date13);

    // // 测试10位秒级时间戳
    // Date date10 = parser.getFileNameDateTime("1748512965.jpg");
    // assertNotNull("应该能解析10位秒级时间戳", date10);
    // }

    /**
     * 测试EXIF日期时间字符串解析
     */
    // @Test
    // public void testParseExifDateTime() {
    // Date date = parser.parseExifDateTime("2023:01:01 12:30:45");
    // assertNotNull("应该能解析EXIF格式的日期时间字符串", date);

    // // 使用时间戳比较，避免Locale和时区问题
    // Calendar cal = Calendar.getInstance();
    // cal.clear();
    // cal.set(2023, Calendar.JANUARY, 1, 12, 30, 45);
    // long expectedTime = cal.getTimeInMillis();
    // assertTrue("日期时间应在预期范围内", Math.abs(date.getTime() - expectedTime) < 1000);

    // // 测试无效的EXIF日期格式
    // Date invalidDate = parser.parseExifDateTime("invalid_date_string");
    // assertNull("无效的EXIF日期格式应该返回null", invalidDate);
    // }

    // private void addAndTestNewSpecialFormat(String fileName, Integer year,
    // Integer month, Integer day) {
    // Date date = parser.getFileNameDateTime(fileName);
    // assertNotNull("新增特殊测试用例: " + fileName + " 应该能解析出日期", date);

    // Calendar cal = Calendar.getInstance();
    // cal.setTime(date);
    // if (year != null) assertEquals("年份应正确", year.intValue(),
    // cal.get(Calendar.YEAR));
    // if (month != null) assertEquals("月份应正确", month.intValue(),
    // cal.get(Calendar.MONTH));
    // if (day != null) assertEquals("日期应正确", day.intValue(),
    // cal.get(Calendar.DAY_OF_MONTH));
    // }
}