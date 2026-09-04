package io.ddd4j.annotation.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiIdempotent} annotation defaults and overrides.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ApiIdempotentTest {

    @Test
    void defaults_shouldHaveExpectedValues() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("defaultsEndpoint");

        assertThat(annotation.value()).isEmpty();
        assertThat(annotation.type()).isEqualTo(ApiIdempotentType.ARGS);
        assertThat(annotation.spel()).isFalse();
        assertThat(annotation.withArgs()).isFalse();
        assertThat(annotation.expireMillis()).isEqualTo(2000L);
        assertThat(annotation.retryTimes()).isZero();
        assertThat(annotation.retryInterval()).isEqualTo(100L);
        assertThat(annotation.unlock()).isFalse();
    }

    @Test
    void value_shouldOverrideDefault() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("customValueEndpoint");

        assertThat(annotation.value()).isEqualTo("my-idempotent-key");
    }

    @Test
    void type_shouldOverrideToToken() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("tokenEndpoint");

        assertThat(annotation.type()).isEqualTo(ApiIdempotentType.TOKEN);
    }

    @Test
    void expireAndRetry_shouldOverrideDefaults() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("configuredEndpoint");

        assertThat(annotation.expireMillis()).isEqualTo(5000L);
        assertThat(annotation.retryTimes()).isEqualTo(3);
        assertThat(annotation.retryInterval()).isEqualTo(200L);
    }

    @Test
    void spelAndWithArgs_shouldBeOverridable() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("spelEndpoint");

        assertThat(annotation.spel()).isTrue();
        assertThat(annotation.withArgs()).isTrue();
    }

    @Test
    void unlock_shouldOverrideDefault() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("autoUnlockEndpoint");

        assertThat(annotation.unlock()).isTrue();
    }

    @Test
    void retention_shouldBeRuntime() {
        assertThat(ApiIdempotent.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    @Test
    void target_shouldIncludeMethodAndType() {
        java.lang.annotation.Target target = ApiIdempotent.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target.value())
                .contains(java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE);
    }

    private ApiIdempotent annotationOf(String methodName) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(methodName);
        ApiIdempotent annotation = method.getAnnotation(ApiIdempotent.class);
        assertThat(annotation).as("@ApiIdempotent on " + methodName).isNotNull();
        return annotation;
    }

    static class Fixture {

        @ApiIdempotent
        void defaultsEndpoint() {
        }

        @ApiIdempotent("my-idempotent-key")
        void customValueEndpoint() {
        }

        @ApiIdempotent(type = ApiIdempotentType.TOKEN)
        void tokenEndpoint() {
        }

        @ApiIdempotent(expireMillis = 5000, retryTimes = 3, retryInterval = 200)
        void configuredEndpoint() {
        }

        @ApiIdempotent(spel = true, withArgs = true)
        void spelEndpoint() {
        }

        @ApiIdempotent(unlock = true)
        void autoUnlockEndpoint() {
        }
    }
}
