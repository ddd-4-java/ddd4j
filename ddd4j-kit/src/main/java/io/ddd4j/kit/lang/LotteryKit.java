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
     * \u6309\u6743\u91cd\u968f\u673a\u62bd\u53d6\u4e00\u4e2a\u5143\u7d20\u3002
     * @param <T> \u53c2\u4e0e\u62bd\u53d6\u7684\u5143\u7d20\u7c7b\u578b
     * @param elements \u53c2\u4e0e\u62bd\u53d6\u7684\u5143\u7d20\u96c6\u5408\uff08\u5185\u90e8\u4f1a\u5148\u6253\u4e71\u987a\u5e8f\uff09
     * @param quotaChecker \u9650\u989d\u68c0\u67e5\u5668\uff1a\u8fd4\u56de\u5143\u7d20\u5269\u4f59\u53ef\u7528\u6570\u91cf\uff0c\u8d1f\u6570\u8868\u793a\u5e93\u5b58\u4e0d\u8db3
     * @return \u62bd\u4e2d\u7684\u5143\u7d20
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
     * \u5728\u7d2f\u79ef\u6982\u7387\u533a\u95f4\u5185\u5b9a\u4f4d\u62bd\u4e2d\u5143\u7d20\uff0c\u9650\u989d\u4e0d\u8db3\u65f6\u9012\u5f52\u91cd\u62bd\u3002
     * @param <T> \u53c2\u4e0e\u62bd\u53d6\u7684\u5143\u7d20\u7c7b\u578b
     * @param totalWeight \u6743\u91cd\u603b\u548c\uff08\u6982\u7387\u533a\u95f4\u603b\u91cf\uff09
     * @param weightSection \u5404\u5143\u7d20\u7684\u7d2f\u79ef\u6982\u7387\u533a\u95f4\u8fb9\u754c\uff08\u5347\u5e8f\uff09
     * @param elements \u53c2\u4e0e\u62bd\u53d6\u7684\u5143\u7d20\u96c6\u5408
     * @param quotaChecker \u9650\u989d\u68c0\u67e5\u5668\uff1a\u8fd4\u56de\u5143\u7d20\u5269\u4f59\u53ef\u7528\u6570\u91cf\uff0c\u8d1f\u6570\u8868\u793a\u5e93\u5b58\u4e0d\u8db3
     * @return \u62bd\u4e2d\u7684\u5143\u7d20
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
     * \u53ef\u53c2\u4e0e\u52a0\u6743\u968f\u673a\u62bd\u53d6\u7684\u5143\u7d20\u6a21\u578b\u3002
     *
     *  <p>\u643a\u5e26\u5956\u52b1\u6570\u91cf\u3001\u51fa\u73b0\u6743\u91cd\u4e0e\u6bcf\u65e5\u9650\u989d\u4e09\u7c7b\u4fe1\u606f\uff0c\u7531\u62bd\u53d6\u65b9\u901a\u8fc7 {@code lottery} \u65b9\u6cd5\u5b9a\u4f4d\u4e2d\u5956\u5143\u7d20\u3002
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
