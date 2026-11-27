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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 同分排序规则枚举类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public enum RankSortTypeEnum {

    /**
     * 同分并列占名次: 1
     * 示例：90, 90, 90, 80, 80, 70, 60, 60, 50, 40
     * 则排名为：1, 1, 1, 4, 4, 6, 6, 6, 9, 10
     */
    SAME_NON_CONTINUOUS("同分并列占名次", 1),

    /**
     * 同分并列不占名次: 2
     * 示例：90, 90, 90, 80, 80, 70, 60, 60, 50, 40
     * 则排名为：1, 1, 1, 2, 2, 3, 4, 4, 5, 6
     */
    SAME_CONTINUOUS("同分并列不占名次", 2),

    /**
     * 同分非并列（可能存在同分两次排名不一样）: 3
     * 示例：90, 90, 90, 80, 80, 70, 60, 60, 50, 40
     * 则排名为：1, 2, 3, 4, 5, 6, 7, 8, 9, 10
     */
    NATURAL("同分非并列", 3);

    /** \u5e76\u5217\u9879\u4e4b\u540e\u4e0d\u7559\u7a7a\u4f4d\u7684\u9ed8\u8ba4\u6392\u540d\u65b9\u5f0f\u3002 */
    private static final RankSortTypeEnum DEFAULT_SORT_TYPE = RankSortTypeEnum.SAME_CONTINUOUS;

    /** \u6392\u540d\u89c4\u5219\u7684\u663e\u793a\u540d\u79f0\u3002 */
    private final String name;

    /** \u6392\u540d\u89c4\u5219\u7684\u6570\u503c\u7f16\u7801\u3002 */
    private final Integer value;

    /**
     * \u6ce8\u518c\u4e00\u79cd\u6392\u540d\u89c4\u5219\u7684\u540d\u79f0\u548c\u7f16\u7801\u3002
     * @param name \u6392\u540d\u89c4\u5219\u7684\u663e\u793a\u540d\u79f0
     * @param value \u6392\u540d\u89c4\u5219\u7684\u6570\u503c\u7f16\u7801
     */
    RankSortTypeEnum(String name, Integer value) {
        this.name = name;
        this.value = value;
    }

    /**
     * 根据排序类型名称查询数值。
     *
     * @param name 排序类型名称
     * @return 对应数值
     */
    public static Integer getValueByName(String name) {
        Map<String, Integer> map = Arrays.stream(RankSortTypeEnum.values()).collect(Collectors.toMap(RankSortTypeEnum::getName, RankSortTypeEnum::getValue));
        return map.get(name);
    }

    /**
     * 根据排序值查询类型名称。
     *
     * @param value 排序值
     * @return 对应名称
     */
    public static String getNameByValue(Integer value) {
        Map<Integer, String> map = Arrays.stream(RankSortTypeEnum.values()).collect(Collectors.toMap(RankSortTypeEnum::getValue, RankSortTypeEnum::getName));
        return map.get(value);
    }

    /**
     * 根据排序值查询枚举项。
     *
     * @param value 排序值
     * @return 对应枚举项
     */
    public static RankSortTypeEnum getEnumByValue(Integer value) {
        for (RankSortTypeEnum loop : RankSortTypeEnum.values()) {
            if (loop.getValue().equals(value)) {
                return loop;
            }
        }
        return DEFAULT_SORT_TYPE;
    }

    /**
     * 获取排序类型名称。
     *
     * @return 排序类型名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取排序类型数值。
     *
     * @return 排序类型数值
     */
    public Integer getValue() {
        return value;
    }

    /**
     * 比较两个排序类型。
     *
     * @param sortType 待比较的排序类型
     * @return 排序类型相同时返回 true
     */
    public boolean equals(RankSortTypeEnum sortType) {
        if (Objects.isNull(sortType)) {
            return Boolean.FALSE;
        }
        return this.compareTo(sortType) == 0;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return name + " -> " + value;
    }

}
