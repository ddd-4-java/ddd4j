package io.ddd4j.sample.quarkus.shiro;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
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
