/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.EMBED_PROPERTIES;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.WILDCARD_VALUE;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Test the CRUD rest API
 *
 * @since 5.7.2
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test.test:test-validation-contrib.xml")
public class DocumentValidationTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    private static final String VALID_DOC = createDocumentJSON("\"Bill\"", "\"Boquet\"");

    private static final String INVALID_DOC = createDocumentJSON("\"   \"", "\"   \"");

    private static final String INVALID_DOC_NOT_DIRTY = createDocumentJSON(null, "\"Missing\"", "\"Mydescription\"");

    private static final String INVALID_DOC_NAME_SEGMENT_LIMIT_EXCEEDED = createDocumentJSON(
            "\"123456789_123456789_123456789\"", "\"The mandatory description\"", "\"Bill\"", "\"Boquet\"");

    private static String createDocumentJSON(String firstname, String lastname) {
        return createDocumentJSON("\"doc1\"", "\"The mandatory description\"", firstname, lastname);
    }

    private static String createDocumentJSON(String description, String firstname, String lastname) {
        return createDocumentJSON("\"doc1\"", description, firstname, lastname);
    }

    private static String createDocumentJSON(String docName, String description, String firstname, String lastname) {
        String doc = "{";
        doc += "\"entity-type\":\"document\" ,";
        doc += "\"name\":" + docName + " ,";
        doc += "\"type\":\"ValidatedDocument\" ,";
        doc += "\"properties\" : {";
        if (description != null) {
            doc += "\"vs:description\": " + description + ", ";
        }
        doc += "\"vs:users\" : [ { \"firstname\" : " + firstname + " , \"lastname\" : " + lastname + "} ]";
        doc += "}}";
        return doc;
    }

    @Test
    public void testCreateValidDocumentEndpointId() {
        DocumentModel root = session.getDocument(new PathRef("/"));
        httpClient.buildPostRequest("/id/" + root.getId())
                  .entity(VALID_DOC)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
    }

    @Test
    public void testCreateValidDocumentEndpointPath() {
        httpClient.buildPostRequest("/path/")
                  .entity(VALID_DOC)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
    }

    @Test
    public void testCreateDocumentWithSegmentLimitViolationInName() {
        // create a doc whose name is too long
        httpClient.buildPostRequest("/path/")
                  .entity(INVALID_DOC_NAME_SEGMENT_LIMIT_EXCEEDED)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED),
                          // check the path in the response
                          node -> assertEquals("/123456789_123456789_1234", node.get("path").asText()));

        // refresh the session
        txFeature.nextTransaction();

        // the created doc's name has been truncated to match the path segment limit
        assertTrue(session.exists(new PathRef("/123456789_123456789_1234")));
    }

    @Test
    public void testCreateDocumentWithViolationEndpointId() {
        DocumentModel root = session.getDocument(new PathRef("/"));
        httpClient.buildPostRequest("/id/" + root.getId())
                  .entity(INVALID_DOC)
                  .executeAndConsume(new JsonNodeHandler(SC_UNPROCESSABLE_ENTITY), this::checkResponseHasErrors);
    }

    @Test
    public void testCreateDocumentWithViolationEndpointPath() {
        httpClient.buildPostRequest("/path/")
                  .entity(INVALID_DOC)
                  .executeAndConsume(new JsonNodeHandler(SC_UNPROCESSABLE_ENTITY), this::checkResponseHasErrors);
    }

    /**
     * NXP-23267
     */
    @Test
    public void testCreateDocumentWithViolationNotDirtyEndpointId() {
        DocumentModel root = session.getDocument(new PathRef("/"));
        httpClient.buildPostRequest("/id/" + root.getId())
                  .entity(INVALID_DOC_NOT_DIRTY)
                  .executeAndConsume(new JsonNodeHandler(SC_UNPROCESSABLE_ENTITY), this::checkResponseHasNotDirtyError);
    }

    /**
     * NXP-23267
     */
    @Test
    public void testCreateDocumentWithViolationNotDirtyEndpointPath() {
        httpClient.buildPostRequest("/path/")
                  .entity(INVALID_DOC_NOT_DIRTY)
                  .executeAndConsume(new JsonNodeHandler(SC_UNPROCESSABLE_ENTITY), this::checkResponseHasNotDirtyError);
    }

    protected void checkResponseHasNotDirtyError(JsonNode node) {
        assertTrue(node.get("has_error").asBoolean());
        assertEquals(1, node.get("number").asInt());
        JsonNode violations = node.get("violations");
        JsonNode violation1 = violations.elements().next();
        assertEquals("NotNullConstraint", violation1.get("constraint").get("name").textValue());
    }

    @Test
    public void testSaveValidDocumentEndpointId() {
        DocumentModel doc = session.createDocumentModel("/", "doc1", "ValidatedDocument");
        doc.setPropertyValue("vs:description", "Mandatory description");
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity(VALID_DOC)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    @Test
    public void testSaveValidDocumentEndpointPath() {
        DocumentModel doc = session.createDocumentModel("/", "doc1", "ValidatedDocument");
        doc.setPropertyValue("vs:description", "Mandatory description");
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        httpClient.buildPutRequest("/path" + doc.getPathAsString())
                  .entity(VALID_DOC)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    @Test
    public void testSaveDocumentWithViolationEndpointId() {
        DocumentModel doc = session.createDocumentModel("/", "doc1", "ValidatedDocument");
        doc.setPropertyValue("vs:description", "Mandatory description");
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity(INVALID_DOC)
                  .executeAndConsume(new JsonNodeHandler(SC_UNPROCESSABLE_ENTITY), this::checkResponseHasErrors);
    }

    @Test
    public void testSaveDocumentWithViolationEndpointPath() {
        DocumentModel doc = session.createDocumentModel("/", "doc1", "ValidatedDocument");
        doc.setPropertyValue("vs:description", "Mandatory description");
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        httpClient.buildPutRequest("/path" + doc.getPathAsString())
                  .entity(INVALID_DOC)
                  .executeAndConsume(new JsonNodeHandler(SC_UNPROCESSABLE_ENTITY), this::checkResponseHasErrors);
    }

    @Test
    public void testPropertyLoading() {
        DocumentModel doc = session.createDocumentModel("/", "doc1", "ValidatedDocument");
        doc.setPropertyValue("vs:description", "Mandatory description");
        doc.getProperty("userRefs").addValue("user:Administrator");
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        httpClient.buildGetRequest("/path" + doc.getPathAsString())
                  .addQueryParameter("embed", "*")
                  .addHeader(EMBED_PROPERTIES, WILDCARD_VALUE)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    /**
     * @since 11.1
     */
    @Test
    public void testGlobalValidationMessage() {
        DocumentModel root = session.getDocument(new PathRef("/"));
        httpClient.buildPostRequest("/id/" + root.getId())
                  .entity(createDocumentJSON("\"Bill\"", "\"Bill\""))
                  .executeAndConsume(new JsonNodeHandler(SC_UNPROCESSABLE_ENTITY), node -> {
                      assertTrue(node.get("has_error").asBoolean());
                      assertEquals(1, node.get("number").asInt());
                      JsonNode violations = node.get("violations");
                      JsonNode violation1 = violations.elements().next();
                      assertEquals("lastname.cannot.be.equals.to.firstname", violation1.get("messageKey").textValue());
                  });
    }

    private void checkResponseHasErrors(JsonNode node) {
        assertTrue(node.get("has_error").asBoolean());
        assertEquals(2, node.get("number").asInt());
        JsonNode violations = node.get("violations");
        JsonNode violation1 = violations.elements().next();
        assertEquals("PatternConstraint", violation1.get("constraint").get("name").textValue());
        JsonNode violation2 = violations.elements().next();
        assertEquals("PatternConstraint", violation2.get("constraint").get("name").textValue());
    }

}
