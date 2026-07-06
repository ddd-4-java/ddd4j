package io.ddd4j.annotation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BusinessType} enum.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class BusinessTypeTest {

    @Test
    void values_shouldContainExpectedEntries() {
        assertThat(BusinessType.values())
                .contains(BusinessType.LOGIN, BusinessType.LOGOUT, BusinessType.INSERT,
                        BusinessType.DELETE, BusinessType.UPDATE, BusinessType.SELECT);
    }

    @Test
    void getKey_shouldReturnIdentifier() {
        assertThat(BusinessType.LOGIN.getKey()).isEqualTo("login");
        assertThat(BusinessType.INSERT.getKey()).isEqualTo("insert");
    }

    @Test
    void getDesc_shouldReturnDescription() {
        assertThat(BusinessType.LOGIN.getDesc()).isEqualTo("用户登录");
        assertThat(BusinessType.UPLOAD.getDesc()).isEqualTo("文件上传");
    }

    @Test
    void toMap_shouldContainKeyAndDesc() {
        Map<String, String> map = BusinessType.LOGIN.toMap();

        assertThat(map).containsEntry("key", "login");
        assertThat(map).containsEntry("desc", "用户登录");
    }

    @Test
    void toList_shouldContainAllEntries() {
        List<Map<String, String>> list = BusinessType.toList();

        assertThat(list).hasSize(BusinessType.values().length);
        assertThat(list.get(0)).containsKey("key");
        assertThat(list.get(0)).containsKey("desc");
    }

    @Test
    void equals_shouldReturnTrueForSameType() {
        assertThat(BusinessType.LOGIN.equals(BusinessType.LOGIN)).isTrue();
        assertThat(BusinessType.LOGIN.equals(BusinessType.LOGOUT)).isFalse();
    }

    @Test
    void setKeyAndSetDesc_shouldRoundTrip() {
        BusinessType type = BusinessType.LOGIN;
        type.setKey("custom-key");
        type.setDesc("custom-desc");

        assertThat(type.getKey()).isEqualTo("custom-key");
        assertThat(type.getDesc()).isEqualTo("custom-desc");

        // restore to avoid leaking state into other tests (enum is singleton)
        type.setKey("login");
        type.setDesc("用户登录");
    }
}
