package io.ddd4j.sample.quarkus;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * 独立商品示例依旧单独运行其回归用例。
 */
public final class GoodsStandaloneProfile implements QuarkusTestProfile {
    /**
     * 通过 <code>Map.of()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>Map.of("sample.goods.standalone", "true")</code>
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("sample.goods.standalone", "true");
    }
}
