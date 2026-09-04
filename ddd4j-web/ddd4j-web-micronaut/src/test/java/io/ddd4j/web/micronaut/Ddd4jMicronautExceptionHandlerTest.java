package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class Ddd4jMicronautExceptionHandlerTest {

    @Test
    void constructorRejectsNullTranslator() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautExceptionHandler((WebExceptionTranslator) null));
    }

    @Test
    void defaultConstructorCreatesDefaultTranslator() {
        Ddd4jMicronautExceptionHandler handler = new Ddd4jMicronautExceptionHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void handleTranslatesClientErrorToResponse() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/test");
        IllegalArgumentException ex = new IllegalArgumentException("bad arg");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(400);
        assertThat(response.body()).isNotNull();
    }

    @Test
    void handleTranslatesServerErrorToResponse() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/crash");
        RuntimeException ex = new RuntimeException("internal failure");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isGreaterThanOrEqualTo(500);
    }

    @Test
    void handleTranslatesConflictForIllegalState() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/conflict");
        IllegalStateException ex = new IllegalStateException("not allowed");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(409);
    }

    @Test
    void handleTranslatesUnauthorized() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/secure");
        SecurityException ex = new SecurityException("denied");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(403);
    }
}