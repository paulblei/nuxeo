/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Guillaume Renard <grenard@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.io.marshallers.json.types.SchemaJsonWriter;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 9.1
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(init = RestServerInit.class, cleanup = Granularity.METHOD)
public class SchemaTest {

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void testFieldsWithConstraintsFetch() {
        // Given the dublincore

        // When I call the schema Rest endpoint
        httpClient.buildGetRequest("/schema/dublincore")
                  .addQueryParameter("fetch." + SchemaJsonWriter.ENTITY_TYPE, SchemaJsonWriter.FETCH_FIELDS)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then it returns the dublincore schema Json with constraints
                      JsonNode fields = node.get("fields");
                      assertNotNull(fields);

                      JsonNode creator = fields.get("creator");
                      assertNotNull(creator);

                      JsonNode type = creator.get("type");
                      assertNotNull(type);
                      assertEquals("string", type.textValue());

                      JsonNode constraints = creator.get("constraints");
                      assertNotNull(constraints);
                      assertTrue(constraints.isArray());
                      assertFalse(constraints.isEmpty());

                      JsonNode contributors = fields.get("contributors");
                      assertNotNull(contributors);

                      type = contributors.get("type");
                      assertNotNull(type);
                      assertEquals("string[]", type.textValue());

                      constraints = contributors.get("constraints");
                      assertNotNull(constraints);
                      assertTrue(constraints.isArray());

                      JsonNode itemConstraints = contributors.get("itemConstraints");
                      assertNotNull(itemConstraints);
                      assertTrue(itemConstraints.isArray());
                      assertFalse(itemConstraints.isEmpty());
                  });
    }
}
