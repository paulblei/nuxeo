/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 */
package org.nuxeo.ecm.restapi.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(init = RestServerInit.class, cleanup = Granularity.METHOD)
public class IntrospectionTests {

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void itCanFetchSchemas() {
        httpClient.buildGetRequest("/config/schemas").executeAndConsume(new JsonNodeHandler(), node -> {
            assertFalse(node.isEmpty());
            boolean dcFound = false;
            for (int i = 0; i < node.size(); i++) {
                if ("dublincore".equals(node.get(i).get("name").asText())) {
                    dcFound = true;
                    break;
                }
            }
            assertTrue(dcFound);
        });
    }

    @Test
    public void itCanFetchASchema() {
        httpClient.buildGetRequest("/config/schemas/dublincore")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

                      JsonAssert jsonAssert = JsonAssert.on(response.getEntityString());

                      jsonAssert.has("name").isEquals("dublincore");
                      jsonAssert.has("@prefix").isEquals("dc");
                      jsonAssert.has("fields.creator").isEquals("string");
                      jsonAssert.has("fields.contributors").isEquals("string[]");
                  }));
    }

    @Test
    public void itCanFetchFacets() {
        httpClient.buildGetRequest("/config/facets").executeAndConsume(new JsonNodeHandler(), node -> {
            assertFalse(node.isEmpty());

            boolean found = false;
            for (int i = 0; i < node.size(); i++) {
                if ("HasRelatedText".equals(node.get(i).get("name").asText())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        });
    }

    @Test
    public void itCanFetchAFacet() {
        httpClient.buildGetRequest("/config/facets/HasRelatedText").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("HasRelatedText", node.get("name").asText());
            assertEquals("relatedtext", node.get("schemas").get(0).get("name").asText());
        });
    }

    @Test
    public void itCanFetchTypes() {
        httpClient.buildGetRequest("/config/types").executeAndConsume(new JsonNodeHandler(), node -> {
            // the export is done as a compound object rather than an array !
            assertTrue(node.has("doctypes"));
            assertTrue(node.has("schemas"));

            assertTrue(node.get("doctypes").has("File"));
            assertTrue(node.get("schemas").has("dublincore"));
        });
    }

    @Test
    public void itCanFetchAType() {
        httpClient.buildGetRequest("/config/types/File").executeAndConsume(new JsonNodeHandler(), node -> {

            // the export is done as a compound object rather than an array !
            assertEquals("Document", node.get("parent").asText());

            boolean dcFound = false;
            JsonNode schemas = node.get("schemas");
            for (int i = 0; i < schemas.size(); i++) {
                if ("dublincore".equals(schemas.get(i).get("name").asText())) {
                    dcFound = true;
                    break;
                }
            }
            assertTrue(dcFound);
        });
    }

}
