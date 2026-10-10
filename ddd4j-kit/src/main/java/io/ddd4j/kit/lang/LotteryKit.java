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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 加权随机工具类（通用算法）。
 *
 * <p>提供基于权重的随机选择能力，可用于抽奖、负载均衡、A/B 测试分流等场景。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class LotteryKit {

    /**
     * 按权重随机抽取一个元素。
     *
     * @param <T> 参与抽取的元素类型
     * @param elements 参与抽取的元素集合（内部会先打乱顺序）
     * @param quotaChecker 限额检查器：返回元素剩余可用数量，负数表示库存不足
     * @return 抽中的元素
     */
    public static <T extends LotteryElement> T lottery(List<T> elements, Function<T, Long> quotaChecker) {
        // 1、打乱元素集合
        Collections.shuffle(elements);
        // 2、定义总的概率区间
        float totalWeight = 0f;
        // 3、存储每个元素新的概率区间
        List<Float> weightSection = new ArrayList<Float>();
        weightSection.add(0F);
        // 4、遍历每个元素，设置概率区间，总的概率区间为每个概率区间的总和
        for (LotteryElement element : elements) {
            totalWeight += element.getWeight();
            weightSection.add(totalWeight);
        }
        // 5、获取总的概率区间中的随机数
        return lottery(totalWeight, weightSection, elements, quotaChecker);
    }

    /**
     * 在累积概率区间内定位抽中元素，限额不足时递归重抽。
     *
     * @param <T> 参与抽取的元素类型
     * @param totalWeight 权重总和（概率区间总量）
     * @param weightSection 各元素的累积概率区间边界（升序）
     * @param elements 参与抽取的元素集合
     * @param quotaChecker 限额检查器：返回元素剩余可用数量，负数表示库存不足
     * @return 抽中的元素
     */
    protected static <T extends LotteryElement> T lottery(float totalWeight, List<Float> weightSection, List<T> elements, Function<T, Long> quotaChecker) {
        // 1、获取总的概率区间中的随机数
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float randomWeight = (float) random.nextInt((int) Math.max(1, totalWeight));
        // 2、判断取到的随机数在哪个元素的概率区间中
        for (int i = 0; i < weightSection.size(); i++) {
            if (randomWeight >= weightSection.get(i) && randomWeight < weightSection.get(i + 1)) {
                T element = elements.get(i);
                // 2.1、检查抽中的元素是否有设置限额
                if (Objects.nonNull(element.getQuota())) {
                    // 2.2、检查限额是否用完（即库存是否还有）
                    Long leaveAmount = quotaChecker.apply(element);
                    // 2.3、库存不足则重新获取一次随机元素
                    if (leaveAmount < 0) {
                        return lottery(totalWeight, weightSection, elements, quotaChecker);
                    }
                }
                //2.4、未设置限额，则返回匹配的元素
                return element;
            }
        }
        // 7、逻辑上不会找不到，以下代码是备用逻辑取奖励数量最少的元素
        return elements.stream().filter(el -> Objects.isNull(el.getQuota())).min(Comparator.comparing(LotteryElement::getAmount)).get();
    }

    /**
     * 可参与加权随机抽取的元素模型。
     *
     * <p>携带奖励数量、出现权重与每日限额三类信息，由抽取方通过 {@code lottery} 方法定位中奖元素。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LotteryElement {

        /**
         * 奖励数量
         */
        private Long amount;
        /**
         * 奖励出现概率
         */
        private Long weight;
        /**
         * 奖励每天出现的限额
         */
        private Integer quota;

    }

}
