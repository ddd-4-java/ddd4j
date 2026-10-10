/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
     * 获取前一个分钟值以 0 或者 5 结尾的时间点（单位：毫秒）
     *
     * @param baseTime 基准时间
     * @return 前一个分钟值以 0 或 5 结尾的时间点，单位毫秒
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
     * 获取下一个分钟值以 0 或者 5 结尾的时间点（单位：毫秒）
     *
     * @param baseTime 基准时间
     * @return 下一个分钟值以 0 或 5 结尾的时间点，单位毫秒
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
     * 获取前一个分钟值以 0 结尾的时间点（单位：毫秒）
     *
     * @param baseTime 基准时间
     * @return 前一个分钟值以 0 结尾的时间点，单位毫秒
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
     * 获取下一个分钟值以 0 结尾的时间点（单位：毫秒）
     *
     * @param baseTime 基准时间
     * @return 下一个分钟值以 0 结尾的时间点，单位毫秒
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
     * 计算两个时间差（Date 重载）："X天Y小时Z分钟"
     *
     * @param startDate 起始时间
     * @param endDate 结束时间
     * @return 格式化后的时间差字符串
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
     * 计算两个时间差（LocalDate 重载）："X天Y小时Z分钟W秒"
     *
     * @param startDateTime 起始日期
     * @param endDateTime 结束日期
     * @return 格式化后的时间差字符串
     */
    public static String getDatePoor(LocalDate startDateTime, LocalDate endDateTime) {
        Duration duration = Duration.between(startDateTime, endDateTime);
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return Math.abs(duration.toDays()) + "天" + hours + "小时" + minutes + "分钟" + seconds + "秒";
    }

    /**
     * 计算两个时间差（LocalDateTime 重载）："X天Y小时Z分钟W秒"
     *
     * @param startDateTime 起始日期时间
     * @param endDateTime 结束日期时间
     * @return 格式化后的时间差字符串
     */
    public static String getDatePoor(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Duration duration = Duration.between(startDateTime, endDateTime);
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return Math.abs(duration.toDays()) + "天" + hours + "小时" + minutes + "分钟" + seconds + "秒";
    }

    /**
     * LocalDateTime → Date
     *
     * @param temporalAccessor 本地日期时间
     * @return 按系统时区转换后的日期对象
     */
    public static Date toDate(LocalDateTime temporalAccessor) {
        ZonedDateTime zdt = temporalAccessor.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    /**
     * LocalDate → Date
     *
     * @param temporalAccessor 本地日期
     * @return 当天零点按系统时区转换后的日期对象
     */
    public static Date toDate(LocalDate temporalAccessor) {
        LocalDateTime localDateTime = LocalDateTime.of(temporalAccessor, LocalTime.of(0, 0, 0));
        ZonedDateTime zdt = localDateTime.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    /**
     * 毫秒 → LocalDateTime
     *
     * @param time 毫秒时间戳
     * @return 按系统时区转换后的本地日期时间
     */
    public static LocalDateTime millsToLocalDateTime(long time) {
        return LocalDateTime.ofInstant(new Date(time).toInstant(), ZoneId.systemDefault());
    }
}
