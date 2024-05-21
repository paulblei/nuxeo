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
 *     Antoine Taillefer
 */
package org.nuxeo.ecm.restapi.server.jaxrs.search.test;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.schema.types.PrimitiveType.PRIMITIVE_TYPE_STRICT_VALIDATION_PROPERTY;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.schema.XSDTypes;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.restapi.test.JsonNodeHelper;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 2023.7
 */
@RunWith(FeaturesRunner.class)
@Features(SearchRestFeature.class)
public class SearchPrimitiveTypePredicatesTest {

    protected static final String PP_TEST_PRIMITIVE_TYPE_PREDICATES = "test_primitive_type_predicates";

    protected static final String PP_EXECUTE_PATH = String.format("search/pp/%s/execute",
            PP_TEST_PRIMITIVE_TYPE_PREDICATES);

    @Inject
    protected CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    // doc with primitive type fields set to 0 or 0.0
    protected DocumentModel doc0;

    // doc with primitive type fields set to 1 or 1.0
    protected DocumentModel doc1;

    @Before
    public void before() {
        doc0 = session.createDocumentModel("/", "doc0", "PrimitiveTypes");
        doc0.setPropertyValue("pt:integerField", 0);
        doc0.setPropertyValue("pt:longField", 0L);
        doc0.setPropertyValue("pt:floatField", 0F);
        doc0.setPropertyValue("pt:doubleField", 0D);
        doc0 = session.createDocument(doc0);

        doc1 = session.createDocumentModel("/", "doc1", "PrimitiveTypes");
        doc1.setPropertyValue("pt:integerField", 1);
        doc1.setPropertyValue("pt:longField", 1L);
        doc1.setPropertyValue("pt:floatField", 1F);
        doc1.setPropertyValue("pt:doubleField", 1D);
        doc1 = session.createDocument(doc1);

        txFeature.nextTransaction();
    }

    /**
     * A field of type "xs:integer" is implemented as a {@link LongType}, see {@link XSDTypes}, yet let's test it anyway
     * as it is a primitive type commonly used.
     */
    @Test
    public void testIntegerType() {
        testPrimitiveType("integerField", false);
    }

    @Test
    @WithFrameworkProperty(name = PRIMITIVE_TYPE_STRICT_VALIDATION_PROPERTY, value = "true")
    public void testIntegerTypeStrictValidation() throws IOException {
        testPrimitiveTypeStrictValidation("integerField");
    }

    @Test
    public void testLongType() {
        testPrimitiveType("longField", false);
    }

    @Test
    @WithFrameworkProperty(name = PRIMITIVE_TYPE_STRICT_VALIDATION_PROPERTY, value = "true")
    public void testLongTypeStrictValidation() throws IOException {
        testPrimitiveTypeStrictValidation("longField");
    }

    /**
     * A field of type "xs:float" is implemented as a {@link DoubleType}, see {@link XSDTypes}, yet let's test it anyway
     * as it is a primitive type commonly used.
     */
    @Test
    public void testFloatType() {
        testPrimitiveType("floatField", true);
    }

    @Test
    @WithFrameworkProperty(name = PRIMITIVE_TYPE_STRICT_VALIDATION_PROPERTY, value = "true")
    public void testFloatTypeStrictValidation() throws IOException {
        testPrimitiveTypeStrictValidation("floatField");
    }

    @Test
    public void testDoubleType() {
        testPrimitiveType("doubleField", true);
    }

    @Test
    @WithFrameworkProperty(name = PRIMITIVE_TYPE_STRICT_VALIDATION_PROPERTY, value = "true")
    public void testDoubleTypeStrictValidation() throws IOException {
        testPrimitiveTypeStrictValidation("doubleField");
    }

    protected void testPrimitiveType(String predicateFieldName, boolean testFloatNotation) {
        // match document with primitive type field value = 1
        httpClient.buildGetRequest(PP_EXECUTE_PATH)
                  .addQueryParameter(predicateFieldName, "1")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                      assertEquals(1, entries.size());
                      JsonNode entry = entries.get(0);
                      assertEquals(doc1.getId(), entry.get("uid").asText());
                  });

        // match no documents with primitive type field value = 2
        httpClient.buildGetRequest(PP_EXECUTE_PATH)
                  .addQueryParameter(predicateFieldName, "2")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertTrue(JsonNodeHelper.getEntries(node).isEmpty()));

        if (testFloatNotation) {
            // match document with primitive type field value = 1.0
            httpClient.buildGetRequest(PP_EXECUTE_PATH)
                      .addQueryParameter(predicateFieldName, "1.0")
                      .executeAndConsume(new JsonNodeHandler(), node -> {
                          List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                          assertEquals(1, entries.size());
                          JsonNode entry = entries.get(0);
                          assertEquals(doc1.getId(), entry.get("uid").asText());
                      });

            // match no documents with primitive type field value = 2.0
            httpClient.buildGetRequest(PP_EXECUTE_PATH)
                      .addQueryParameter(predicateFieldName, "2.0")
                      .executeAndConsume(new JsonNodeHandler(),
                              node -> assertTrue(JsonNodeHelper.getEntries(node).isEmpty()));
        }

        // bad query parameter, match document with primitive type field value = 0 or 0.0
        httpClient.buildGetRequest(PP_EXECUTE_PATH)
                  .addQueryParameter(predicateFieldName, "foo")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                      assertEquals(1, entries.size());
                      JsonNode entry = entries.get(0);
                      assertEquals(doc0.getId(), entry.get("uid").asText());
                  });
    }

    protected void testPrimitiveTypeStrictValidation(String predicateFieldName) {
        // bad query parameter, mustn't match any document with primitive type field value = 0 or 0.0
        httpClient.buildGetRequest(PP_EXECUTE_PATH)
                  .addQueryParameter(predicateFieldName, "foo")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> {
                      assertEquals("exception", node.get("entity-type").asText());
                      assertEquals("400", node.get("status").asText());
                      assertTrue(node.get("message").asText().startsWith("java.lang.NumberFormatException"));
                  });
    }

}
