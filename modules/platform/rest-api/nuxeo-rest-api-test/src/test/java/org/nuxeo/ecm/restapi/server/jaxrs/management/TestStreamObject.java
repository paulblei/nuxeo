/*
 * (C) Copyright 2023 Nuxeo (http://nuxeo.com/) and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * @since 2021.35
 */
@WithFrameworkProperty(name = "metrics.streams.enabled", value = "true")
public class TestStreamObject extends ManagementBaseTest {

    @Test
    public void testListStreams() {
        httpClient.buildGetRequest("/management/stream/streams").executeAndConsume(new JsonNodeHandler(), result -> {
            assertTrue(result.isArray());
            assertFalse(result.isEmpty());
            assertEquals("avro", result.get(0).get("codec").asText());
        });
    }

    @Test
    public void testStreamIntrospection() {
        httpClient.buildGetRequest("/management/stream")
                  .executeAndConsume(new JsonNodeHandler(), result -> assertTrue(result.isObject()));
    }

    @Test
    public void testScale() {
        httpClient.buildGetRequest("/management/stream/scale").executeAndConsume(new JsonNodeHandler(), result -> {
            assertTrue(result.isObject());
            assertTrue(result.at("/scale").isObject());
        });
    }

}
