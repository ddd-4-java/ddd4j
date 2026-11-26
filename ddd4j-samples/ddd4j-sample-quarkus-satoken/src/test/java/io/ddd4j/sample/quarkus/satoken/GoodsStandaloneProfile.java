package io.ddd4j.sample.quarkus.satoken;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public final class GoodsStandaloneProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("sample.goods.standalone", "true");
    }
}
