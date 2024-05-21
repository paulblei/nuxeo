/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
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
 *
 * Contributors:
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.http.test;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.function.ThrowableConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.core.MediaType;

/**
 * @since 2023.13
 */
class HttpClientTestRuleLogger {

    private static final Logger log = LogManager.getLogger(HttpClientTestRuleLogger.class);

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpClientTestRuleLogger() {
        // nothing
    }

    protected static void logDebugInfo(HttpClientTestRule httpClient, HttpUriRequest request, HttpResponse response) {
        String requestLog = buildRequestLog(httpClient, request);
        String responseLog = buildResponseLog(response);
        log.error("""
                An error occurred during HTTP request execution or during HTTP response handling:
                    HTTP request:
                {}
                    HTTP response:
                {}
                """, requestLog.indent(8), responseLog.indent(8));
    }

    protected static String buildRequestLog(HttpClientTestRule httpClient, HttpUriRequest request) {
        StringBuilder builder = new StringBuilder();
        try {
            builder.append(request.getMethod())
                   .append(" ")
                   .append(request.getURI())
                   .append(" ")
                   .append(request.getProtocolVersion())
                   .append(System.lineSeparator());
            builder.append("Headers:").append(System.lineSeparator());
            for (var header : httpClient.headers.entrySet()) {
                // only print global header if request doesn't override it
                if (request.getFirstHeader(header.getKey()) == null) {
                    builder.append("    ")
                            .append(header.getKey())
                            .append(": ")
                            .append(header.getValue())
                            .append(System.lineSeparator());
                }
            }
            for (var header : request.getAllHeaders()) {
                builder.append("    ")
                       .append(header.getName())
                       .append(": ")
                       .append(header.getValue())
                       .append(System.lineSeparator());
            }
            builder.append("Body:").append(System.lineSeparator());
            if (request instanceof HttpEntityEnclosingRequest enclosingRequest) {
                var apacheEntity = enclosingRequest.getEntity();
                if (apacheEntity.isRepeatable() || apacheEntity.getContent().available() > 0) {
                    var contentTypeHeader = request.getFirstHeader("Content-Type");
                    var contentType = contentTypeHeader == null ? "application/octet-stream"
                            : contentTypeHeader.getValue();
                    builder.append(prettyPrint(contentType, apacheEntity.getContent()).indent(4));
                } else {
                    builder.append("Body not available, either it was consumed or it is not repeatable".indent(4));
                }
            }
        } catch (Exception e) {
            builder.append("Marshalling error").append(e);
        }
        return builder.toString();
    }

    protected static String buildResponseLog(HttpResponse response) {
        StringBuilder builder = new StringBuilder();
        try {
            var apacheResponse = response.response;
            var statusLine = apacheResponse.getStatusLine();
            builder.append(statusLine.getStatusCode())
                   .append(" ")
                   .append(StringUtils.defaultIfBlank(statusLine.getReasonPhrase(), "No Reason Phrase sent by Server"))
                   .append(" ")
                   .append(statusLine.getProtocolVersion())
                   .append(System.lineSeparator());
            builder.append("Headers:").append(System.lineSeparator());
            for (var header : apacheResponse.getAllHeaders()) {
                builder.append("    ")
                       .append(header.getName())
                       .append(": ")
                       .append(header.getValue())
                       .append(System.lineSeparator());
            }
            builder.append("Body:").append(System.lineSeparator());
            response.getResponseStream().ifPresent(ThrowableConsumer.asConsumer(stream -> {
                if (stream.available() <= 0) {
                    stream.reset();
                }
                builder.append(prettyPrint(response.getType(), stream).indent(4));
            }));
        } catch (Exception e) {
            builder.append("Marshalling error: ").append(e);
        }
        return builder.toString();
    }

    protected static String prettyPrint(String contentType, InputStream stream) throws IOException {
        String body;
        if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON)) {
            body = jsonPrettyPrint(stream);
        } else {
            body = IOUtils.toString(stream, UTF_8);
        }
        return body.replace("\\n", System.lineSeparator()).replace("\\t", "\t");
    }

    protected static String jsonPrettyPrint(InputStream stream) throws IOException {
        // deserialize with Jackson to have a pretty print
        var node = MAPPER.readTree(stream);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
}
