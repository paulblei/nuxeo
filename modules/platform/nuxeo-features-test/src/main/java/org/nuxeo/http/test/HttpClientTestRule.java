/*
 * (C) Copyright 2018-2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.http.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.nuxeo.http.test.HttpClientTestRuleLogger.logDebugInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * This is an HttpClient wrapped as a JUnit {@link TestRule} to perform needed cleanup on teardown.
 * <p>
 * In a unit test with an embedded Nuxeo, the client can be instantiated like below:
 * 
 * <pre>
 * &#64;Inject
 * protected ServletContainerFeature servletContainerFeature;
 * 
 * &#64;Rule
 * public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
 *         () -> servletContainerFeature.getHttpUrl());
 * </pre>
 * 
 * The client is now ready to execute requests on {@code http://localhost:PORT}. The default JSON client has the
 * following configuration:
 * <ul>
 * <li>uses Administrator:Administrator basic auth</li>
 * <li>sends the Accept header with application/json value</li>
 * <li>sends the Content-Type header with application/json value</li>
 * </ul>
 * The most interesting way to use the client is with {@link ResponseHandler handler} and {@code executeAnd*} APIs, for
 * example:
 * 
 * <pre>
 * httpClient.buildGetRequest("/api/v1/me").executeAndConsume(new JsonNodeHandler(), node -> {
 *     assertEquals("user", node.get("entity-type").asText());
 *     assertEquals("Administrator", node.get("id").asText());
 * });
 * </pre>
 * 
 * In this example, we execute a GET request to {@code /api/v1/me} endpoint, and then we consume the HTTP response with
 * the {@link org.nuxeo.http.test.handler.JsonNodeHandler} handler which asserts that the HTTP response status code has
 * the value {@code 200}, asserts that the header {@code Content-Type} has the {@code application/json} value,
 * deserializes the response entity to a {@link com.fasterxml.jackson.databind.JsonNode}, and gives it to the lambda
 * passed as second parameter.
 * <p>
 * When an error or an assertion failure occurs, in the handler or custom assertion, the client will automatically log
 * the HTTP request and response related to the failure.
 *
 * @since 2023.13
 * @see HttpClientTestRule#builder()
 * @see HttpClientTestRule#defaultClient(Supplier)
 * @see HttpClientTestRule#defaultJsonClient(Supplier)
 * @see org.nuxeo.http.test.handler
 */
public class HttpClientTestRule implements TestRule {

    public static final String ADMINISTRATOR = "Administrator";

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    protected final Supplier<String> url;

    protected final Duration timeout;

    protected final Map<String, String> headers;

    protected final boolean redirectsEnabled;

    protected CloseableHttpClient client;

    private HttpClientTestRule(Builder builder) {
        this.url = builder.url;
        this.timeout = builder.timeout;
        this.headers = builder.headers;
        this.redirectsEnabled = builder.redirectsEnabled;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                starting();
                try {
                    base.evaluate();
                } finally {
                    finished();
                }
            }
        };
    }

    public void starting() {
        var requestConfig = RequestConfig.custom()
                                         // override all 3 as they are usually set with the same default value
                                         .setConnectTimeout((int) timeout.toMillis())
                                         .setSocketTimeout((int) timeout.toMillis())
                                         .setConnectionRequestTimeout((int) timeout.toMillis())
                                         .setRedirectsEnabled(redirectsEnabled)
                                         .build();
        var headers = this.headers.entrySet()
                                  .stream()
                                  .map(entry -> new BasicHeader(entry.getKey(), entry.getValue()))
                                  .toList();
        client = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).setDefaultHeaders(headers).build();
    }

    public void finished() {
        try {
            client.close();
        } catch (IOException e) {
            throw new NuxeoException("Unable to close HTTP client", e);
        }
    }

    /**
     * @return the {@link RequestBuilder} for a GET request
     */
    public RequestBuilder buildGetRequest(String path) {
        return buildRequest(HttpGet.METHOD_NAME, path);
    }

    /**
     * @return the {@link RequestBuilder} for a POST request
     */
    public RequestBuilder buildPostRequest(String path) {
        return buildRequest(HttpPost.METHOD_NAME, path);
    }

    /**
     * @return the {@link RequestBuilder} for a PUT request
     */
    public RequestBuilder buildPutRequest(String path) {
        return buildRequest(HttpPut.METHOD_NAME, path);
    }

    /**
     * @return the {@link RequestBuilder} for a DELETE request
     */
    public RequestBuilder buildDeleteRequest(String path) {
        return buildRequest(HttpDelete.METHOD_NAME, path);
    }

    protected RequestBuilder buildRequest(String method, String path) {
        String url = this.url.get();
        if (!path.isEmpty()) {
            if (!url.endsWith("/") && !path.startsWith("/")) {
                url += '/';
            } else if (url.endsWith("/") && path.startsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            url += path.replace(" ", "%20");
        }
        return new RequestBuilder(method, url);
    }

    /**
     * @return An {@link HttpClientTestRule} targeting the given {@code url} and using the Administrator basic auth
     */
    public static HttpClientTestRule defaultClient(Supplier<String> url) {
        return HttpClientTestRule.builder().url(url).adminCredentials().build();
    }

    /**
     * @return An {@link HttpClientTestRule} targeting the given {@code url}, using the Administrator basic auth, and
     *         having {@code application/json} for {@code Accept} and {@code Content-Type} headers.
     */
    public static HttpClientTestRule defaultJsonClient(Supplier<String> url) {
        return HttpClientTestRule.builder()
                                 .url(url)
                                 .adminCredentials()
                                 .accept(MediaType.APPLICATION_JSON)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .build();
    }

    /**
     * @return An {@link HttpClientTestRule.Builder} to configure precisely your client
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The http client test rule builder. This builder is used to pass default parameters to client and requests.
     */
    public static class Builder {

        private Supplier<String> url;

        private Duration timeout;

        private Map<String, String> headers;

        private boolean redirectsEnabled;

        public Builder() {
            this.url = () -> System.getProperty("nuxeoURL", "http://localhost:8080/nuxeo").replaceAll("/$", "");
            this.timeout = DEFAULT_TIMEOUT;
            this.headers = new HashMap<>();
            this.redirectsEnabled = true;
        }

        public Builder url(String url) {
            this.url = () -> url;
            return this;
        }

        public Builder url(Supplier<String> url) {
            this.url = url;
            return this;
        }

        public Builder adminCredentials() {
            return credentials(ADMINISTRATOR, ADMINISTRATOR);
        }

        public Builder credentials(String username, String password) {
            headers.put(HttpHeaders.AUTHORIZATION, buildBasicAuthorizationValue(username, password));
            return this;
        }

        public Builder accept(String accept) {
            headers.put(HttpHeaders.ACCEPT, accept);
            return this;
        }

        public Builder contentType(String contentType) {
            headers.put(HttpHeaders.CONTENT_TYPE, contentType);
            return this;
        }

        public Builder timeout(int timeout) {
            return timeout(Duration.ofMillis(timeout));
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public Builder redirectsEnabled(boolean redirectsEnabled) {
            this.redirectsEnabled = redirectsEnabled;
            return this;
        }

        public HttpClientTestRule build() {
            return new HttpClientTestRule(this);
        }
    }

    public class RequestBuilder {

        protected final org.apache.http.client.methods.RequestBuilder internalBuilder;

        protected final Map<String, List<String>> queryParameters = new HashMap<>();

        protected final Map<String, List<String>> matrixParameters = new HashMap<>();

        protected RequestBuilder(String method, String url) {
            this.internalBuilder = org.apache.http.client.methods.RequestBuilder.create(method).setUri(url);
        }

        public RequestBuilder credentials(String username, String password) {
            return addHeader(HttpHeaders.AUTHORIZATION, buildBasicAuthorizationValue(username, password));
        }

        public RequestBuilder accept(String accept) {
            internalBuilder.setHeader(HttpHeaders.ACCEPT, accept);
            return this;
        }

        public RequestBuilder contentType(String contentType) {
            internalBuilder.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
            return this;
        }

        public RequestBuilder addHeader(String name, String value) {
            internalBuilder.addHeader(name, value);
            return this;
        }

        public RequestBuilder addHeaders(Map<String, String> headers) {
            headers.forEach(internalBuilder::addHeader);
            return this;
        }

        public RequestBuilder addQueryParameter(String name, String value) {
            queryParameters.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        public RequestBuilder addQueryParameter(String name, String value, String... values) {
            return addQueryParameter(name, value).addQueryParameter(name, values);
        }

        public RequestBuilder addQueryParameter(String name, String[] values) {
            return addQueryParameter(name, List.of(values));
        }

        public RequestBuilder addQueryParameter(String name, List<String> values) {
            values.forEach(value -> addQueryParameter(name, value));
            return this;
        }

        public RequestBuilder addQueryParameters(Map<String, String> parameters) {
            parameters.forEach(this::addQueryParameter);
            return this;
        }

        public RequestBuilder addMatrixParameter(String name, String value) {
            matrixParameters.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        public RequestBuilder entity(String entity) {
            internalBuilder.setEntity(new StringEntity(entity, UTF_8));
            return this;
        }

        public RequestBuilder entity(Map<String, String> formData) {
            var parameters = formData.entrySet()
                                     .stream()
                                     .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                                     .toList();
            internalBuilder.setEntity(new UrlEncodedFormEntity(parameters, UTF_8));
            return this;
        }

        public RequestBuilder entity(MultivaluedMap<String, String> formData) {
            var parameters = //
                    formData.entrySet()
                            .stream()
                            .<NameValuePair> mapMulti( //
                                    (entry, consumer) -> entry.getValue()
                                                              .forEach(value -> consumer.accept(
                                                                      new BasicNameValuePair(entry.getKey(), value))))
                            .toList();
            internalBuilder.setEntity(new UrlEncodedFormEntity(parameters, UTF_8));
            return this;
        }

        public RequestBuilder entity(InputStream entity) {
            var requestStream = new CloningInputStream(entity);
            internalBuilder.setEntity(new InputStreamEntity(requestStream) {
                @Override
                public boolean isRepeatable() {
                    return true;
                }

                @Override
                public InputStream getContent() throws IOException {
                    requestStream.reset();
                    return super.getContent();
                }
            });
            return this;
        }

        private HttpClientExecution _execute() {
            try {
                var uriBuilder = new URIBuilder(internalBuilder.getUri());
                // inject matrix parameters if any
                if (!matrixParameters.isEmpty()) {
                    var pathSegments = uriBuilder.getPathSegments();
                    var lastSegment = pathSegments.remove(pathSegments.size() - 1);
                    String matrixParametersSegment = //
                            matrixParameters.entrySet().stream().<String> mapMulti((entry, consumer) -> {
                                for (var value : entry.getValue()) {
                                    consumer.accept(entry.getKey() + "=" + value);
                                }
                            }).collect(joining(";"));
                    pathSegments.add(lastSegment + ";" + matrixParametersSegment);
                    uriBuilder.setPathSegments(pathSegments);
                }
                // inject query parameters if any
                if (!queryParameters.isEmpty()) {
                    queryParameters.forEach(
                            (key, values) -> values.forEach(value -> uriBuilder.addParameter(key, value)));
                }
                // finally build the uri
                internalBuilder.setUri(uriBuilder.build());
                var request = internalBuilder.build();
                var response = new CloseableHttpResponse(client.execute(request));
                return new HttpClientExecution(request, response);
            } catch (IOException | URISyntaxException e) {
                throw new NuxeoException("An error occurred during HTTP call", e);
            }
        }

        @SuppressWarnings("resource") // not ours to close
        public CloseableHttpResponse execute() {
            return _execute().response();
        }

        public <R> R executeAndThen(Function<? super HttpResponse, R> finisher) {
            return executeAndThen(response -> response, finisher);
        }

        public void executeAndConsume(Consumer<? super HttpResponse> consumer) {
            executeAndConsume(response -> response, consumer);
        }

        public <T> T execute(ResponseHandler<T> responseHandler) {
            try (var execution = _execute()) {
                try {
                    return responseHandler.handleResponse(execution.response());
                } catch (AssertionError e) {
                    logDebugInfo(HttpClientTestRule.this, execution.request(), execution.response());
                    throw e;
                } catch (Exception e) {
                    logDebugInfo(HttpClientTestRule.this, execution.request(), execution.response());
                    throw new NuxeoException("An error occurred during HTTP response handling", e);
                }
            } catch (IOException e) {
                throw new NuxeoException("An error occurred while closing HTTP response", e);
            }
        }

        public <T, R> R executeAndThen(ResponseHandler<T> responseHandler, Function<T, R> finisher) {
            try (var execution = _execute()) {
                try {
                    var handledResponse = responseHandler.handleResponse(execution.response());
                    return finisher.apply(handledResponse);
                } catch (AssertionError e) {
                    logDebugInfo(HttpClientTestRule.this, execution.request(), execution.response());
                    throw e;
                } catch (Exception e) {
                    logDebugInfo(HttpClientTestRule.this, execution.request(), execution.response());
                    throw new NuxeoException("An error occurred during HTTP response handling", e);
                }
            } catch (IOException e) {
                throw new NuxeoException("An error occurred while closing HTTP response", e);
            }
        }

        public <T> void executeAndConsume(ResponseHandler<T> responseHandler, Consumer<T> consumer) {
            try (var execution = _execute()) {
                try {
                    var handledResponse = responseHandler.handleResponse(execution.response());
                    consumer.accept(handledResponse);
                } catch (AssertionError e) {
                    logDebugInfo(HttpClientTestRule.this, execution.request(), execution.response());
                    throw e;
                } catch (Exception e) {
                    logDebugInfo(HttpClientTestRule.this, execution.request(), execution.response());
                    throw new NuxeoException("An error occurred during HTTP response handling", e);
                }
            } catch (IOException e) {
                throw new NuxeoException("An error occurred while closing HTTP response", e);
            }
        }
    }

    private static String buildBasicAuthorizationValue(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ':' + password).getBytes());
    }
}
