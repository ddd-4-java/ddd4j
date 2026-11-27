package io.ddd4j.core.cqrs.readmodel;

import java.time.ZonedDateTime;
import java.util.BitSet;
import java.util.Objects;

/**
 * 轻量平台共用的六段数值 CRON（秒、分、时、日、月、周）。
 * 提取自 Javalin/Vertx/Dropwizard 的相同解析规则：支持数值、列表、范围、步长、星号；
 * 日和周还接受问号通配，周日为 0 或 7，日与周同时限制时取交集。
 * 不接受名称、宏、L/W/# 或年字段；原生平台额外方言不得静默转换。
 * 不创建线程、不选择默认时区，平台按其既有时区传入 ZonedDateTime。
 */
public final class ProjectionCron {
    /** \u5404 Cron \u5b57\u6bb5\u5141\u8bb8\u53d6\u503c\u7684\u4f4d\u96c6\u5408\u3002 */
    private final BitSet[] fields;

    /**
     * \u4ee5\u5404\u5b57\u6bb5\u5df2\u7ecf\u89e3\u6790\u51fa\u7684\u4f4d\u96c6\u5408\u521b\u5efa Cron \u8ba1\u5212\u3002
     * @param fields \u5404\u5b57\u6bb5\u5141\u8bb8\u5339\u914d\u7684\u53d6\u503c\u96c6\u5408
     */
    private ProjectionCron(BitSet[] fields) {
        this.fields = fields;
    }

    /**
     * 校验并解析六段表达式。
     *
     * @param expression 数值 CRON 表达式
     * @return 不可变表达式
     * @throws IllegalArgumentException 字段或数值不合法
     */
    public static ProjectionCron parse(String expression) {
        String[] parts = Objects.requireNonNull(expression, "expression must not be null").trim().split("\\s+");
        if (parts.length != 6) {
            throw new IllegalArgumentException("CRON expression must have 6 fields: " + expression);
        }
        int[] minimum = {0, 0, 0, 1, 1, 0};
        int[] maximum = {59, 59, 23, 31, 12, 7};
        BitSet[] fields = new BitSet[6];
        for (int i = 0; i < parts.length; i++) {
            String field = parts[i];
            if ((i == 3 || i == 5) && "?".equals(field)) {
                field = "*";
            }
            fields[i] = parseField(field, minimum[i], maximum[i]);
        }
        if (fields[5].get(7)) {
            fields[5].set(0);
        }
        return new ProjectionCron(fields);
    }

    /**
     * \u89e3\u6790 Cron \u5b57\u6bb5\u4e2d\u7684\u5355\u503c\u3001\u8303\u56f4\u3001\u6b65\u957f\u548c\u9017\u53f7\u5206\u9694\u9879\u3002
     * @param field Cron \u8868\u8fbe\u5f0f\u4e2d\u7684\u5355\u4e2a\u5b57\u6bb5
     * @param min \u5b57\u6bb5\u5141\u8bb8\u7684\u6700\u5c0f\u503c
     * @param max \u5b57\u6bb5\u5141\u8bb8\u7684\u6700\u5927\u503c
     * @return \u8bb0\u5f55\u5141\u8bb8\u53d6\u503c\u7684\u4f4d\u96c6\u5408
     */
    private static BitSet parseField(String field, int min, int max) {
        BitSet values = new BitSet(max + 1);
        for (String item : field.split(",", -1)) {
            String[] stepParts = item.split("/", -1);
            if (stepParts.length > 2) {
                throw new IllegalArgumentException("Invalid CRON field: " + field);
            }
            int step = stepParts.length == 2 ? Integer.parseInt(stepParts[1]) : 1;
            if (step <= 0) {
                throw new IllegalArgumentException("Invalid CRON step: " + field);
            }
            String range = stepParts[0];
            int start = min;
            int end = max;
            if (!"*".equals(range)) {
                String[] bounds = range.split("-", -1);
                if (bounds.length > 2) {
                    throw new IllegalArgumentException("Invalid CRON range: " + field);
                }
                start = Integer.parseInt(bounds[0]);
                end = bounds.length == 2 ? Integer.parseInt(bounds[1]) : (stepParts.length == 2 ? max : start);
            }
            if (start < min || end > max || start > end) {
                throw new IllegalArgumentException("CRON value out of range: " + field);
            }
            // 正步长可大于字段宽度；long 递增避免合法大步长在 int 边界溢出。
            for (long value = start; value <= end; value += step) {
                values.set((int) value);
            }
        }
        return values;
    }

    /**
     * 求严格晚于指定时间的下一执行时间，保留传入时区并按时间线处理夏令时重叠。
     *
     * @param from 当前带时区时间
     * @return 下一时间；未来八年无匹配（包括不可能的日期）返回 null
     */
    public ZonedDateTime next(ZonedDateTime from) {
        Objects.requireNonNull(from, "from must not be null");
        ZonedDateTime candidate = from.plusSeconds(1).withNano(0);
        ZonedDateTime limit = from.plusYears(8);
        while (candidate.isBefore(limit)) {
            if (!fields[4].get(candidate.getMonthValue()) || !fields[3].get(candidate.getDayOfMonth())
                    || !fields[5].get(candidate.getDayOfWeek().getValue() % 7)) {
                candidate = candidate.toLocalDate().plusDays(1).atStartOfDay(candidate.getZone());
            } else if (!fields[2].get(candidate.getHour())) {
                candidate = candidate.plusHours(1).withMinute(0).withSecond(0);
            } else if (!fields[1].get(candidate.getMinute())) {
                candidate = candidate.plusMinutes(1).withSecond(0);
            } else if (!fields[0].get(candidate.getSecond())) {
                candidate = candidate.plusSeconds(1);
            } else {
                return candidate;
            }
        }
        return null;
    }
}
