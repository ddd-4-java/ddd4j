package io.ddd4j.sample.quarkus;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * 独立商品示例依旧单独运行其回归用例。
 */
public final class GoodsStandaloneProfile implements QuarkusTestProfile {
    /**
     * \u901a\u8fc7 <code>Map.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Map.of("sample.goods.standalone", "true")</code>
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("sample.goods.standalone", "true");
    }
}
