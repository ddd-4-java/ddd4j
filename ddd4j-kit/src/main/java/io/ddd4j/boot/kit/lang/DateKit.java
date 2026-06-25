package io.ddd4j.boot.kit.lang;

import cn.hutool.core.date.DateUtil;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期工具类
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@UtilityClass
public class DateKit extends DateUtil {

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
}