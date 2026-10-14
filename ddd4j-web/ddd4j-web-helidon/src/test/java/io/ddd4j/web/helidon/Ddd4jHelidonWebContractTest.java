/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.helidon;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.testkit.AbstractWebContractTest;
import io.ddd4j.web.testkit.WebContractClient;
import io.ddd4j.web.testkit.WebContractPaths;
import io.ddd4j.web.testkit.WebContractResponse;
import io.helidon.microprofile.server.Server;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jHelidonWebContractTest extends AbstractWebContractTest {

    private final WebContractClient contractClient = new HelidonContractClient();
    private static Server server;
    private static Client httpClient;
    private static WebTarget target;

    @BeforeAll
    static void startServer() {
        System.setProperty("ddd4j.web.public-paths", "/contract/success,/contract/public,/contract/created,"
                + "/contract/context,/contract/idempotent,/contract/errors/**");
        System.setProperty("ddd4j.web.idempotency.cache-name", "helidon-contract");
        server = Server.builder().host("127.0.0.1").port(0)
                .addApplication(new ContractApplication()).build().start();
        httpClient = ClientBuilder.newClient();
        target = httpClient.target("http://127.0.0.1:" + server.port());
    }

    @AfterAll
    static void stopServer() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (server != null) {
            server.stop();
        }
        System.clearProperty("ddd4j.web.public-paths");
        System.clearProperty("ddd4j.web.idempotency.cache-name");
    }

    @BeforeEach
    void setUp() {
        CacheKit.build("helidon-contract", 300L);
        Subject subject = mock(Subject.class);
        when(subject.verify("contract-valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("helidon-contract");
    }

    @Override
    protected WebContractClient client() {
        return contractClient;
    }

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    @ApplicationPath("/")
    public static class ContractApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(Arrays.<Class<?>>asList(ContractResource.class,
                    Ddd4jHelidonRequestFilter.class,
                    Ddd4jHelidonResponseFilter.class,
                    Ddd4jHelidonExceptionMapper.class,
                    Ddd4jHelidonIllegalStateExceptionMapper.class));
        }
    }

    @Path("/contract")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class ContractResource {

        @GET
        @Path("/success")
        public R<Map<String, String>> success() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        @GET
        @Path("/public")
        public R<Map<String, String>> publicEndpoint() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        @GET
        @Path("/protected")
        public R<Map<String, String>> protectedEndpoint() {
            return R.ok(Collections.singletonMap("result", "ok"));
        }

        @POST
        @Path("/created")
        @Consumes(MediaType.APPLICATION_JSON)
        public Response created(String ignoredBody) {
            return Response.status(Response.Status.CREATED)
                    .entity(R.ok(Collections.singletonMap("result", "created"))).build();
        }

        @GET
        @Path("/context")
        public R<Map<String, Object>> context() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", ThreadContext.get(WebContextScope.REQUEST_ID));
            data.put("traceId", ThreadContext.get(WebContextScope.TRACE_ID));
            data.put("tenantId", ThreadContext.get(ContextConstants.TENANT_ID));
            return R.ok(data);
        }

        @POST
        @Path("/idempotent")
        @Consumes(MediaType.APPLICATION_JSON)
        public R<Map<String, String>> idempotent(String ignoredBody) {
            return R.ok(Collections.singletonMap("result", "accepted"));
        }

        @GET
        @Path("/errors/{type}")
        public R<Void> error(@PathParam("type") String type) {
            switch (type) {
                case "bad-request": throw new IllegalArgumentException("bad request");
                case "forbidden": throw new SecurityException("forbidden");
                case "not-found": throw new NoSuchElementException("not found");
                case "conflict": throw new IllegalStateException("conflict");
                case "unsupported-media-type": throw new WebStatusException(415, "unsupported media type");
                case "unprocessable-entity": throw new WebStatusException(422, "unprocessable entity");
                case "too-many-requests": throw new WebStatusException(429, "too many requests");
                default: throw new RuntimeException("internal failure");
            }
        }
    }

    private final class HelidonContractClient implements WebContractClient {

        @Override
        public WebContractResponse request(String method, String path, Map<String, String> headers, String body) {
            Invocation.Builder builder = target.path(path).request();
            headers.forEach(builder::header);
            Response response = Objects.isNull(body)
                    ? builder.method(method)
                    : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
            try (Response closeable = response) {
                Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
                response.getStringHeaders().forEach((name, values) -> responseHeaders.put(name, new ArrayList<>(values)));
                return new WebContractResponse(response.getStatus(), responseHeaders, response.readEntity(String.class));
            }
        }
    }
}
