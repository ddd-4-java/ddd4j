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
     * \u6839\u636e\u6392\u5e8f\u7c7b\u578b\u540d\u79f0\u67e5\u8be2\u6570\u503c\u3002
     * @param name \u6392\u5e8f\u7c7b\u578b\u540d\u79f0
     * @return \u5bf9\u5e94\u6570\u503c
     */
    public static Integer getValueByName(String name) {
        Map<String, Integer> map = Arrays.stream(RankSortTypeEnum.values()).collect(Collectors.toMap(RankSortTypeEnum::getName, RankSortTypeEnum::getValue));
        return map.get(name);
    }

    /**
     * \u6839\u636e\u6392\u5e8f\u503c\u67e5\u8be2\u7c7b\u578b\u540d\u79f0\u3002
     * @param value \u6392\u5e8f\u503c
     * @return \u5bf9\u5e94\u540d\u79f0
     */
    public static String getNameByValue(Integer value) {
        Map<Integer, String> map = Arrays.stream(RankSortTypeEnum.values()).collect(Collectors.toMap(RankSortTypeEnum::getValue, RankSortTypeEnum::getName));
        return map.get(value);
    }

    /**
     * \u6839\u636e\u6392\u5e8f\u503c\u67e5\u8be2\u679a\u4e3e\u9879\u3002
     * @param value \u6392\u5e8f\u503c
     * @return \u5bf9\u5e94\u679a\u4e3e\u9879
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
     * \u83b7\u53d6\u6392\u5e8f\u7c7b\u578b\u540d\u79f0\u3002
     * @return \u6392\u5e8f\u7c7b\u578b\u540d\u79f0
     */
    public String getName() {
        return name;
    }

    /**
     * \u83b7\u53d6\u6392\u5e8f\u7c7b\u578b\u6570\u503c\u3002
     * @return \u6392\u5e8f\u7c7b\u578b\u6570\u503c
     */
    public Integer getValue() {
        return value;
    }

    /**
     * \u6bd4\u8f83\u4e24\u4e2a\u6392\u5e8f\u7c7b\u578b\u3002
     * @param sortType \u5f85\u6bd4\u8f83\u7684\u6392\u5e8f\u7c7b\u578b
     * @return \u6392\u5e8f\u7c7b\u578b\u76f8\u540c\u65f6\u8fd4\u56de true
     */
    public boolean equals(RankSortTypeEnum sortType) {
        if (Objects.isNull(sortType)) {
            return Boolean.FALSE;
        }
        return this.compareTo(sortType) == 0;
    }

    /**
     * 生成用于调试和日志的对象描述。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return name + " -> " + value;
    }

}
