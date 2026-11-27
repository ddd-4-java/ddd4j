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

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 排名工具类（通用算法）。
 *
 * <p>提供基于排序键的排名计算能力，可用于排行榜、成绩排名、优先级队列等场景。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class RankKit {

    /**
     * 根据指定字段进行排名，返回排名后的数据列表
     *
     * @param sortedList   已排序数据列表
     * @param type         分数相同时排名策略
     * @param keyExtractor 键提取器, 计算排名的字段
     * @param <T> 数据元素类型
     * @param <U> 键提取器产生的键类型
     * @return 排名后的列表
     */
    public static <T, U> List<RankModel<T>> rank(RankSortTypeEnum type, List<T> sortedList, Function<? super T, ? extends U>
            keyExtractor) {
        return rank(type, null, sortedList, keyExtractor);
    }

    /**
     * 根据指定字段进行排名，返回排名前top条数据
     *
     * @param sortedList   已排序数据列表
     * @param type         分数相同时排名策略
     * @param top          取前top名数据
     * @param keyExtractor 键提取器, 计算排名的字段
     * @param <T> 数据元素类型
     * @param <U> 键提取器产生的键类型
     * @return 排名后的列表
     */
    public static <T, U> List<RankModel<T>> rank(RankSortTypeEnum type, Integer top, List<T> sortedList, Function<? super T, ? extends U> keyExtractor) {
        switch (type) {
            case SAME_CONTINUOUS:
                return doRankSameContinuous(top, sortedList, keyExtractor);
            case SAME_NON_CONTINUOUS:
                return doRankSameNonContinuous(top, sortedList, keyExtractor);
            default:
                return doRankNatural(top, sortedList);
        }
    }

    /**
     * \u76f8\u540c\u952e\u5e76\u5217\u4e14\u4e4b\u540e\u53ef\u80fd\u8df3\u8fc7\u540d\u6b21\u3002
     * @param <T> \u88ab\u6392\u540d\u7684\u5143\u7d20\u7c7b\u578b
     * @param <U> \u6bd4\u8f83\u662f\u5426\u5e76\u5217\u7684\u6392\u540d\u952e\u7c7b\u578b
     * @param top \u8981\u4fdd\u7559\u7684\u540d\u6b21\u4e0a\u9650\uff0c\u7a7a\u503c\u6216\u975e\u6b63\u6570\u65f6\u4e0d\u9650\u5236
     * @param sortedList \u5df2\u6392\u5e8f\u4e14\u7b49\u5f85\u9644\u52a0\u540d\u6b21\u7684\u8bb0\u5f55
     * @param keyExtractor \u63d0\u53d6\u7528\u4e8e\u5224\u5b9a\u5e76\u5217\u7684\u6392\u540d\u952e
     * @return \u542b\u540d\u6b21\u548c\u539f\u8bb0\u5f55\u7684\u6392\u540d\u5217\u8868
     */
    private static <T, U> List<RankModel<T>> doRankSameNonContinuous(Integer top, List<T> sortedList, Function<? super T, ? extends U> keyExtractor) {
        List<RankModel<T>> result = new ArrayList<>();
        int rankNumber = 0;
        int rowNumber = 1;
        U currentKey = null;
        for (T item : sortedList) {
            U loopKey = keyExtractor.apply(item);
            rankNumber = loopKey.equals(currentKey) ? rankNumber : rowNumber;
            if (Objects.nonNull(top) && top > 0 && rankNumber > top) {
                break;
            }
            result.add(new RankModel<>(rankNumber, item));
            currentKey = loopKey;
            rowNumber = rowNumber + 1;
        }
        return result;
    }

    /**
     * \u76f8\u540c\u952e\u5e76\u5217\u4e14\u4e4b\u540e\u8fde\u7eed\u8ba1\u540d\u6b21\u3002
     * @param <T> \u88ab\u6392\u540d\u7684\u5143\u7d20\u7c7b\u578b
     * @param <U> \u6bd4\u8f83\u662f\u5426\u5e76\u5217\u7684\u6392\u540d\u952e\u7c7b\u578b
     * @param top \u8981\u4fdd\u7559\u7684\u540d\u6b21\u4e0a\u9650\uff0c\u7a7a\u503c\u6216\u975e\u6b63\u6570\u65f6\u4e0d\u9650\u5236
     * @param sortedList \u5df2\u6392\u5e8f\u4e14\u7b49\u5f85\u9644\u52a0\u540d\u6b21\u7684\u8bb0\u5f55
     * @param keyExtractor \u63d0\u53d6\u7528\u4e8e\u5224\u5b9a\u5e76\u5217\u7684\u6392\u540d\u952e
     * @return \u542b\u540d\u6b21\u548c\u539f\u8bb0\u5f55\u7684\u6392\u540d\u5217\u8868
     */
    private static <T, U> List<RankModel<T>> doRankSameContinuous(Integer top, List<T> sortedList, Function<? super T, ? extends U> keyExtractor) {
        List<RankModel<T>> result = new ArrayList<>();
        int rankNumber = 0;
        U currentKey = null;
        for (T item : sortedList) {
            U loopKey = keyExtractor.apply(item);
            rankNumber = loopKey.equals(currentKey) ? rankNumber : rankNumber + 1;
            if (Objects.nonNull(top) && top > 0 && rankNumber > top) {
                break;
            }
            result.add(new RankModel<>(rankNumber, item));
            currentKey = loopKey;
        }
        return result;
    }

    /**
     * \u6309\u5217\u8868\u987a\u5e8f\u9010\u884c\u8ba1\u540d\u6b21\u3002
     * @param <T> \u88ab\u6392\u540d\u7684\u5143\u7d20\u7c7b\u578b
     * @param top \u8981\u4fdd\u7559\u7684\u540d\u6b21\u4e0a\u9650\uff0c\u7a7a\u503c\u6216\u975e\u6b63\u6570\u65f6\u4e0d\u9650\u5236
     * @param sortedList \u5df2\u6392\u5e8f\u4e14\u7b49\u5f85\u9644\u52a0\u540d\u6b21\u7684\u8bb0\u5f55
     * @return \u542b\u540d\u6b21\u548c\u539f\u8bb0\u5f55\u7684\u6392\u540d\u5217\u8868
     */
    private static <T> List<RankModel<T>> doRankNatural(Integer top, List<T> sortedList) {
        List<RankModel<T>> result = new ArrayList<>();
        int rankNumber = 1;
        for (T item : sortedList) {
            if (Objects.nonNull(top) && top > 0 && rankNumber > top) {
                break;
            }
            result.add(new RankModel<>(rankNumber, item));
            rankNumber = rankNumber + 1;
        }
        return result;
    }

    /**
     * 排名实体模型
     */
    public static class RankModel<T> {
        /**
         * 排名
         */
        private Integer rank;

        /**
         * 数据对象
         */
        private T data;

        /** \u521b\u5efa\u7a7a\u6392\u5e8f\u9879\u3002 */
        public RankModel() {
        }

        /**
         * \u521b\u5efa\u5305\u542b\u6392\u5e8f\u503c\u548c\u6570\u636e\u7684\u6392\u5e8f\u9879\u3002
         * @param rank \u6392\u5e8f\u503c
         * @param data \u5173\u8054\u6570\u636e
         */
        public RankModel(Integer rank, T data) {
            this.rank = rank;
            this.data = data;
        }

        /**
         * \u83b7\u53d6\u6392\u5e8f\u503c\u3002
         * @return \u6392\u5e8f\u503c
         */
        public Integer getRank() {
            return rank;
        }

        /**
         * \u8bbe\u7f6e\u6392\u5e8f\u503c\u3002
         * @param rank \u6392\u5e8f\u503c
         */
        public void setRank(Integer rank) {
            this.rank = rank;
        }

        /**
         * \u83b7\u53d6\u5173\u8054\u6570\u636e\u3002
         * @return \u5173\u8054\u6570\u636e
         */
        public T getData() {
            return data;
        }

        /**
         * \u8bbe\u7f6e\u5173\u8054\u6570\u636e\u3002
         * @param data \u5173\u8054\u6570\u636e
         */
        public void setData(T data) {
            this.data = data;
        }
    }

}
