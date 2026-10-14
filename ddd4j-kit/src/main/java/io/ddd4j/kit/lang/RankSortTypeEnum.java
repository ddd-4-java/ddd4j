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

    private static final RankSortTypeEnum DEFAULT_SORT_TYPE = RankSortTypeEnum.SAME_CONTINUOUS;

    private final String name;

    private final Integer value;

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

    @Override
    public String toString() {
        return name + " -> " + value;
    }

}
