package io.ddd4j.kit.lang;

import cn.hutool.core.date.DateUtil;
import lombok.experimental.UtilityClass;

import java.time.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 日期工具类（合并 ddd4j-core/util/DateUtils 的全部 9 个方法 + ddd4j-kit 原 DateKit 的 getMonthsBetween）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class DateKit extends DateUtil {

    /**
     * \u5217\u51fa\u4e24\u4e2a\u65f6\u95f4\u70b9\u4e4b\u95f4\u7684\u6708\u4efd\uff08\u5305\u542b\u9996\u5c3e\u6708\u4efd\uff09\u3002
     * @param startDateTime \u8d77\u59cb\u65f6\u95f4\uff08\u542b\u6240\u5728\u6708\u4efd\uff09
     * @param endDateTime \u7ed3\u675f\u65f6\u95f4\uff08\u542b\u6240\u5728\u6708\u4efd\uff09
     * @return \u6309\u6708\u9012\u589e\u7684\u5e74\u6708\u5217\u8868
     */
    public static List<String> getMonthsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<String> months = new ArrayList<>();
        YearMonth startYearMonth = YearMonth.from(startDateTime);
        YearMonth endYearMonth = YearMonth.from(endDateTime);
        while (!startYearMonth.isAfter(endYearMonth)) {
            months.add(startYearMonth.toString());
            startYearMonth = startYearMonth.plusMonths(1);
        }
        return months;
    }

    // ============ 从 ddd4j-core/util/DateUtils 合并（基于 java.util.Date）============

    /**
     * \u83b7\u53d6\u524d\u4e00\u4e2a\u5206\u949f\u503c\u4ee5 0 \u6216\u8005 5 \u7ed3\u5c3e\u7684\u65f6\u95f4\u70b9\uff08\u5355\u4f4d\uff1a\u6beb\u79d2\uff09
     *
     * @param baseTime \u53c2\u8003\u65f6\u95f4
     * @return \u5bf9\u9f50\u540e\u7684\u65f6\u95f4\u6233\uff0c\u5355\u4f4d\u6beb\u79d2
     */
    public static long getPreviousMillisEndWithMinute0or5(Date baseTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseTime);
        int minute = calendar.get(Calendar.MINUTE);
        if (minute < 5) {
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTime().getTime();
        }

        int minus = minute % 5 < 5 ? minute % 5 : 0;

        calendar.add(Calendar.MINUTE, -minus);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime();
    }

    /**
     * \u83b7\u53d6\u4e0b\u4e00\u4e2a\u5206\u949f\u503c\u4ee5 0 \u6216\u8005 5 \u7ed3\u5c3e\u7684\u65f6\u95f4\u70b9\uff08\u5355\u4f4d\uff1a\u6beb\u79d2\uff09
     *
     * @param baseTime \u53c2\u8003\u65f6\u95f4
     * @return \u5bf9\u9f50\u540e\u7684\u65f6\u95f4\u6233\uff0c\u5355\u4f4d\u6beb\u79d2
     */
    public static long getNextMillisEndWithMinute0or5(Date baseTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseTime);
        int minute = calendar.get(Calendar.MINUTE);
        if (minute < 55) {
            int mod = minute % 5;
            int add = mod < 5 ? 5 - mod : 0;
            calendar.add(Calendar.MINUTE, add);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTime().getTime();
        }
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date endTime = DateUtil.offsetHour(calendar.getTime(), 1);
        return endTime.getTime();
    }

    /**
     * \u83b7\u53d6\u524d\u4e00\u4e2a\u5206\u949f\u503c\u4ee5 0 \u7ed3\u5c3e\u7684\u65f6\u95f4\u70b9\uff08\u5355\u4f4d\uff1a\u6beb\u79d2\uff09
     *
     * @param baseTime \u53c2\u8003\u65f6\u95f4
     * @return \u5bf9\u9f50\u540e\u7684\u65f6\u95f4\u6233\uff0c\u5355\u4f4d\u6beb\u79d2
     */
    public static long getPreviousMillisEndWithMinute0(Date baseTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseTime);
        int minute = calendar.get(Calendar.MINUTE);
        if (minute < 10) {
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTime().getTime();
        }

        int minus = minute % 10 == 0 ? 10 : minute % 10;

        calendar.add(Calendar.MINUTE, -minus);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime().getTime();
    }

    /**
     * \u83b7\u53d6\u4e0b\u4e00\u4e2a\u5206\u949f\u503c\u4ee5 0 \u7ed3\u5c3e\u7684\u65f6\u95f4\u70b9\uff08\u5355\u4f4d\uff1a\u6beb\u79d2\uff09
     *
     * @param baseTime \u53c2\u8003\u65f6\u95f4
     * @return \u5bf9\u9f50\u540e\u7684\u65f6\u95f4\u6233\uff0c\u5355\u4f4d\u6beb\u79d2
     */
    public static long getNextMillisEndWithMinute0(Date baseTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseTime);
        int minute = calendar.get(Calendar.MINUTE);
        if (minute < 50) {
            int mod = minute % 10;
            int add = mod < 10 ? 10 - mod : 0;
            calendar.add(Calendar.MINUTE, add);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTime().getTime();
        }
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date endTime = DateUtil.offsetHour(calendar.getTime(), 1);
        return endTime.getTime();
    }

    /**
     * \u8ba1\u7b97\u4e24\u4e2a\u65f6\u95f4\u5dee\uff08Date \u91cd\u8f7d\uff09\uff1a"X\u5929Y\u5c0f\u65f6Z\u5206\u949f"
     *
     * @param startDate \u8d77\u59cb\u65f6\u95f4
     * @param endDate \u7ed3\u675f\u65f6\u95f4
     * @return \u4e2d\u6587\u5929\u3001\u5c0f\u65f6\u3001\u5206\u949f\u5dee\u503c
     */
    public static String getDatePoor(Date startDate, Date endDate) {
        long nd = 1000L * 24 * 60 * 60;
        long nh = 1000L * 60 * 60;
        long nm = 1000L * 60;
        long diff = endDate.getTime() - startDate.getTime();
        long day = diff / nd;
        long hour = diff % nd / nh;
        long min = diff % nd % nh / nm;
        return day + "天" + hour + "小时" + min + "分钟";
    }

    /**
     * \u8ba1\u7b97\u4e24\u4e2a\u65f6\u95f4\u5dee\uff08LocalDate \u91cd\u8f7d\uff09\uff1a"X\u5929Y\u5c0f\u65f6Z\u5206\u949fW\u79d2"
     *
     * @param startDateTime \u8d77\u59cb\u65f6\u95f4
     * @param endDateTime \u7ed3\u675f\u65f6\u95f4
     * @return \u4e2d\u6587\u5929\u3001\u5c0f\u65f6\u3001\u5206\u949f\u3001\u79d2\u5dee\u503c
     */
    public static String getDatePoor(LocalDate startDateTime, LocalDate endDateTime) {
        Duration duration = Duration.between(startDateTime, endDateTime);
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return Math.abs(duration.toDays()) + "天" + hours + "小时" + minutes + "分钟" + seconds + "秒";
    }

    /**
     * \u8ba1\u7b97\u4e24\u4e2a\u65f6\u95f4\u5dee\uff08LocalDateTime \u91cd\u8f7d\uff09\uff1a"X\u5929Y\u5c0f\u65f6Z\u5206\u949fW\u79d2"
     *
     * @param startDateTime \u8d77\u59cb\u65f6\u95f4
     * @param endDateTime \u7ed3\u675f\u65f6\u95f4
     * @return \u4e2d\u6587\u5929\u3001\u5c0f\u65f6\u3001\u5206\u949f\u3001\u79d2\u5dee\u503c
     */
    public static String getDatePoor(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Duration duration = Duration.between(startDateTime, endDateTime);
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return Math.abs(duration.toDays()) + "天" + hours + "小时" + minutes + "分钟" + seconds + "秒";
    }

    /**
     * LocalDateTime \u2192 Date
     *
     * @param temporalAccessor \u5f85\u8f6c\u6362\u7684\u672c\u5730\u65e5\u671f\u6216\u65e5\u671f\u65f6\u95f4
     * @return \u6309\u7cfb\u7edf\u9ed8\u8ba4\u65f6\u533a\u8f6c\u6362\u7684 Date
     */
    public static Date toDate(LocalDateTime temporalAccessor) {
        ZonedDateTime zdt = temporalAccessor.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    /**
     * LocalDate \u2192 Date
     *
     * @param temporalAccessor \u5f85\u8f6c\u6362\u7684\u672c\u5730\u65e5\u671f\u6216\u65e5\u671f\u65f6\u95f4
     * @return \u6309\u7cfb\u7edf\u9ed8\u8ba4\u65f6\u533a\u8f6c\u6362\u7684 Date
     */
    public static Date toDate(LocalDate temporalAccessor) {
        LocalDateTime localDateTime = LocalDateTime.of(temporalAccessor, LocalTime.of(0, 0, 0));
        ZonedDateTime zdt = localDateTime.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    /**
     * \u6beb\u79d2 \u2192 LocalDateTime
     *
     * @param time \u6beb\u79d2\u65f6\u95f4\u6233
     * @return \u7cfb\u7edf\u9ed8\u8ba4\u65f6\u533a\u4e2d\u7684\u672c\u5730\u65e5\u671f\u65f6\u95f4
     */
    public static LocalDateTime millsToLocalDateTime(long time) {
        return LocalDateTime.ofInstant(new Date(time).toInstant(), ZoneId.systemDefault());
    }
}
