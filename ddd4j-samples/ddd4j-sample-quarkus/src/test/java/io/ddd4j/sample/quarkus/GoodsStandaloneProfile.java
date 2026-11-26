package io.ddd4j.sample.quarkus;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * 独立商品示例依旧单独运行其回归用例。
 */
public final class GoodsStandaloneProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("sample.goods.standalone", "true");
    }
}
