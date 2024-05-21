/*
 * (C) Copyright 2019 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Guillaume Renard
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.directory.test.DirectoryFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Test doc resolved doc field re-posted.
 *
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, DirectoryFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test:test-directory-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.restapi.test.test:test-multi-resolved-fields-docTypes.xml")
public class DocWithMultiResolvedFieldTest {

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void testRePostResolvedXVocabularyEntrySameParentDirectory() {
        createDocumentThenRePostResolved("mr:coverages", "Albania");
    }

    @Test
    public void testRePostResolvedXVocabularyEntryDifferentParentDirectory() {
        createDocumentThenRePostResolved("mr:countries", "Algeria");
    }

    protected void createDocumentThenRePostResolved(String propertyName, String value) {
        String requestEntity = """
                {
                  "entity-type": "document",
                  "name": "doc1",
                  "type": "MultiResolved",
                  "properties": {
                    "%s": ["%s"]
                  }
                }
                """.formatted(propertyName, value);
        String entity = httpClient.buildPostRequest("/path/")
                                  .entity(requestEntity)
                                  .addHeader("fetch-document", "properties")
                                  .addHeader("properties", "*")
                                  .addHeader("fetch-directoryEntry", "parent")
                                  .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> {
                                      assertNotNull(node);
                                      JsonNode props = node.get("properties");
                                      assertNotNull(props);
                                      assertTrue(props.has(propertyName));
                                      ArrayNode propertyValue = (ArrayNode) props.get(propertyName);
                                      assertEquals(1, propertyValue.size());
                                      JsonNode firstPropertyValue = propertyValue.get(0);
                                      assertTrue(firstPropertyValue.isObject());
                                      assertTrue(firstPropertyValue.has("properties"));
                                      assertTrue(firstPropertyValue.get("properties").has("parent"));
                                      assertTrue(firstPropertyValue.get("properties").get("parent").isObject());
                                      return node.toString();
                                  });
        // Re-Post identical
        httpClient.buildPutRequest("/path/doc1")
                  .entity(entity)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

}
