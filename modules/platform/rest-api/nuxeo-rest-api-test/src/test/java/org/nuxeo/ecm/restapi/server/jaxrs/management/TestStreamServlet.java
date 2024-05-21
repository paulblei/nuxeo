/*
 * (C) Copyright 2022 Nuxeo (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.ecm.restapi.server.jaxrs.management;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.HttpResponse;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;

/**
 * @since 2021.22
 */
@RunWith(FeaturesRunner.class)
@Features({ ServletContainerFeature.class, PlatformFeature.class })
@Deploy("org.nuxeo.ecm.platform.restapi.test")
@Deploy("org.nuxeo.ecm.platform.restapi.server")
@Deploy("org.nuxeo.ecm.platform.restapi.test:test-stream-servlet-contrib.xml")
public class TestStreamServlet {

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> servletContainerFeature.getHttpUrl());

    @Test
    public void testStreamServlet() {
        String url = "/api/v1/management/stream/cat";

        httpClient.buildGetRequest(url).addQueryParameter("timeout", "50ms").executeAndConsume(response -> {
            // stream param is missing
            assertEquals(SC_BAD_REQUEST, response.getStatus());
            assertContentTypeStartsWith(response, "application/json");
            assertEquals("{\"status\": 400,\"message\":\"Missing stream param\"}", response.getEntityString());
        });

        httpClient.buildGetRequest(url)
                  .addQueryParameter("timeout", "50ms")
                  .addQueryParameter("stream", "bulk/unknownStream")
                  .executeAndConsume(response -> {
                      // stream not found
                      assertEquals(SC_NOT_FOUND, response.getStatus());
                      assertContentTypeStartsWith(response, "application/json");
                  });

        httpClient.buildGetRequest(url)
                  .addQueryParameter("timeout", "50ms")
                  .addQueryParameter("stream", "internal/metrics")
                  .addQueryParameter("fromGroup", "stream/introspection")
                  .executeAndConsume(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      assertContentTypeStartsWith(response, "text/event-stream");
                      String content = response.getEntityString();
                      assertTrue(content, content.startsWith("data: "));
                  });
    }

    protected void assertContentTypeStartsWith(HttpResponse response, String expected) {
        var contentType = response.getOptFirstHeader("Content-Type")
                                  .orElseThrow(() -> new AssertionError("'Content-Type' must be present"));
        assertTrue(contentType, contentType.startsWith(expected));
    }

}
