package io.ddd4j.sample.quarkus.satoken;

import com.google.common.collect.ImmutableMap;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public final class GoodsStandaloneProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return ImmutableMap.of("sample.goods.standalone", "true");
    }
}
