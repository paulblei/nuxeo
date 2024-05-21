/*
 * (C) Copyright 2013-2021 Nuxeo (http://nuxeo.com/) and others.
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

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter.ENTITY_TYPE;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.FETCH_PROPERTIES;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.HEADER_PREFIX;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.ecm.collections.api.CollectionManager;
import org.nuxeo.ecm.collections.api.FavoritesManager;
import org.nuxeo.ecm.collections.core.io.CollectionsJsonEnricher;
import org.nuxeo.ecm.collections.core.io.FavoritesJsonEnricher;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.marshallers.json.document.ACPJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.BasePermissionsJsonEnricher;
import org.nuxeo.ecm.core.io.registry.MarshallingConstants;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.permissions.ACLJsonEnricher;
import org.nuxeo.ecm.platform.preview.io.PreviewJsonEnricher;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.ecm.platform.tag.io.TagsJsonEnricher;
import org.nuxeo.ecm.platform.thumbnail.io.ThumbnailJsonEnricher;
import org.nuxeo.ecm.restapi.jaxrs.io.RestConstants;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Test the CRUD rest API
 *
 * @since 5.7.2
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.platform.thumbnail:OSGI-INF/marshallers-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.preview:OSGI-INF/marshallers-contrib.xml")
@Deploy("org.nuxeo.ecm.permissions:OSGI-INF/marshallers-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.collections.core")
@Deploy("org.nuxeo.ecm.platform.userworkspace")
public class DocumentBrowsingTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> restServerFeature.getRestApiUrl())
                                                                   .adminCredentials()
                                                                   .contentType(MediaType.APPLICATION_JSON)
                                                                   .header("X-NXDocumentProperties", "dublincore")
                                                                   .build();

    @Test
    public void iCanBrowseTheRepoByItsPath() {
        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);
        // When i do a GET Request
        httpClient.buildGetRequest("/path" + note.getPathAsString())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then i get a document
                          node -> assertDocumentEqualsNode(note, node));
    }

    @Test
    public void iCanBrowseTheRepoByItsId() {
        // Given a document
        DocumentModel note = RestServerInit.getNote(0, session);
        // When i do a GET Request
        httpClient.buildGetRequest("/id/" + note.getId())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then i get a document
                          node -> assertDocumentEqualsNode(note, node));
    }

    @Test
    public void iCanGetTheChildrenOfADoc() {
        // Given a folder with one document
        DocumentModel folder = RestServerInit.getFolder(0, session);
        DocumentModel child = session.createDocumentModel(folder.getPathAsString(), "note", "Note");
        child = session.createDocument(child);
        transactionalFeature.nextTransaction();

        DocumentModel childFinal = child;
        // When i call a GET on the children for that doc
        httpClient.buildGetRequest("/id/" + folder.getId() + "/@children")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i get the only document of the folder
                      Iterator<JsonNode> elements = node.get("entries").elements();
                      node = elements.next();
                      assertDocumentEqualsNode(childFinal, node);
                  });
    }

    @Test
    public void iCanUpdateADocument() throws Exception {
        // Given a document
        DocumentModel note = RestServerInit.getNote(0, session);
        JSONDocumentNode jsonDoc = httpClient.buildGetRequest("/id/" + note.getId())
                                             .addHeader("X-NXDocumentProperties", "dublincore")
                                             .executeAndThen(new JSONDocumentNodeHandler(), json -> {
                                                 // When i do a PUT request on the document with modified data
                                                 // and the same change token
                                                 String changeToken = json.node.get("changeToken").asText();
                                                 assertNotNull(changeToken);
                                                 return json;
                                             });
        jsonDoc.setPropertyValue("dc:title", "New title");
        httpClient.buildPutRequest("/id/" + note.getId())
                  .entity(jsonDoc.asJson())
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
        // Then the document is updated
        transactionalFeature.nextTransaction();
        note = RestServerInit.getNote(0, session);
        assertEquals("New title", note.getTitle());
    }

    @Test
    public void iCanUpdateADocumentWithAComment() throws Exception {
        DocumentModel note = RestServerInit.getNote(0, session);
        JSONDocumentNode jsonDoc = httpClient.buildGetRequest("/id/" + note.getId())
                                             .addHeader("X-NXDocumentProperties", "dublincore")
                                             .execute(new JSONDocumentNodeHandler());
        jsonDoc.setPropertyValue("dc:title", "Another title");
        try (CapturingEventListener listener = new CapturingEventListener(DocumentEventTypes.BEFORE_DOC_UPDATE)) {
            httpClient.buildPutRequest("/id/" + note.getId())
                      .addHeader(RestConstants.UPDATE_COMMENT_HEADER, "a simple comment")
                      .entity(jsonDoc.asJson())
                      .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

            transactionalFeature.nextTransaction();
            note = RestServerInit.getNote(0, session);
            assertEquals("Another title", note.getTitle());
            EventContext ctx = listener.findLastCapturedEventContextOrElseThrow();
            assertEquals("a simple comment", ctx.getProperty("comment"));
        }
    }

    @Test
    public void iCannotUpdateADocumentWithOldChangeToken() throws Exception {
        // Given a document
        DocumentModel note = RestServerInit.getNote(0, session);
        String noteId = note.getId();
        JSONDocumentNode jsonDoc = httpClient.buildGetRequest("/id/" + noteId)
                                             .addHeader("X-NXDocumentProperties", "dublincore")
                                             .execute(new JSONDocumentNodeHandler());

        // When i do a PUT request on the document with modified data and pass an old/invalid change token
        jsonDoc.setPropertyValue("dc:title", "New title");
        jsonDoc.node.put("changeToken", "9999-1234"); // old/invalid change token

        // Then we get a 409 CONFLICT
        httpClient.buildPutRequest("/id/" + noteId)
                  .entity(jsonDoc.asJson())
                  .executeAndConsume(new JsonNodeHandler(SC_CONFLICT), node -> {
                      // Assert the response is a JSON entity
                      String error = JsonNodeHelper.getErrorMessage(node);
                      assertEquals(noteId + ", Invalid change token", error);
                  });

        // And the document is NOT updated
        transactionalFeature.nextTransaction();
        note = RestServerInit.getNote(0, session);
        assertEquals("Note 0", note.getTitle()); // still old title
    }

    @Test
    public void iCanUpdateAFileDocumentWithoutErasingBlob() {
        DocumentModel doc = session.createDocumentModel("/", "myFile", "File");
        Blob blob = new StringBlob("test");
        blob.setFilename("test.txt");
        doc.setProperty("file", "content", blob);
        doc.setPropertyValue("dc:title", "my Title");
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();

        var statusCodeHandler = new HttpStatusCodeHandler();
        httpClient.buildGetRequest("/id/" + doc.getId())
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_OK, status.intValue()));

        String payload = """
                {
                  "entity-type": "document",
                  "state": "project",
                  "title": "New title",
                  "properties": {
                    "dc:description": "blabla",
                    "dc:title": "New title"
                  }
                }
                """;
        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity(payload)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();

        doc = session.getDocument(new IdRef(doc.getId()));
        assertEquals("New title", doc.getTitle());
        Blob value = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(value);
        assertEquals("test.txt", value.getFilename());
    }

    @Test
    public void iCanUpdateDocumentVersion() throws Exception {
        // Given a document
        DocumentModel note = RestServerInit.getNote(0, session);
        // Check the current version of the live document
        // it's a note - version at creation and for each updates
        assertEquals("0.1", note.getVersionLabel());

        JSONDocumentNode jsonDoc = httpClient.buildGetRequest("/id/" + note.getId())
                                             .addHeader("X-NXDocumentProperties", "dublincore")
                                             .execute(new JSONDocumentNodeHandler());

        // When i do a PUT request on the document with modified version in the header
        jsonDoc.setPropertyValue("dc:title", "New title !");
        httpClient.buildPutRequest("/id/" + note.getId())
                  .addHeader(RestConstants.X_VERSIONING_OPTION, VersioningOption.MAJOR.toString())
                  .addHeader(HEADER_PREFIX + FETCH_PROPERTIES + "." + ENTITY_TYPE, "versionLabel")
                  .entity(jsonDoc.asJson())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Check if the version of the document has been returned
                          node -> assertEquals("1.0", node.get("versionLabel").asText()));

        // Check if the original document is still not versioned.
        note = RestServerInit.getNote(0, session);
        assertEquals("0.1", note.getVersionLabel());
    }

    @Test
    public void itCanUpdateADocumentWithoutSpecifyingIdInJSONPayload() {
        var statusCodeHandler = new HttpStatusCodeHandler();
        // Given a document
        DocumentModel note = RestServerInit.getNote(0, session);
        httpClient.buildGetRequest("/path" + note.getPathAsString())
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_OK, status.intValue()));

        // When i do a PUT request on the document with modified data
        httpClient.buildPutRequest("/id/" + note.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dc:title\":\"Other New title\"}}")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();
        note = RestServerInit.getNote(0, session);
        assertEquals("Other New title", note.getTitle());
    }

    @Test
    public void itCanSetPropertyToNull() {
        DocumentModel note = RestServerInit.getNote(0, session);
        note.setPropertyValue("dc:format", "a value that will be set to null");
        note.setPropertyValue("dc:language", "a value that must not be reseted");
        session.saveDocument(note);

        transactionalFeature.nextTransaction();

        // When i do a PUT request on the document with modified data
        httpClient.buildPutRequest("/id/" + note.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dc:format\":null}}")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();
        note = RestServerInit.getNote(0, session);
        assertNull(note.getPropertyValue("dc:format"));
        assertEquals("a value that must not be reseted", note.getPropertyValue("dc:language"));
    }

    @Test
    public void itCanSetPropertyToNullNewModeKeepEmpty() {
        DocumentModel note = RestServerInit.getNote(0, session);
        note.setPropertyValue("dc:format", "a value that will be set to null");
        session.saveDocument(note);

        transactionalFeature.nextTransaction();

        // When i do a PUT request on the document with modified data
        httpClient.buildPutRequest("/id/" + note.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dc:format\":\"\"}}")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();
        note = RestServerInit.getNote(0, session);
        Serializable value = note.getPropertyValue("dc:format");
        if (!"".equals(value)) {
            // will be NULL for Oracle, where empty string and NULL are the same thing
            assertNull(value);
        }
    }

    /*
     * NXP-25280
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server:test-defaultvalue-docTypes.xml")
    public void itCanSetSimplePropertyToNullInsideComplexOne() {
        DocumentModel doc = session.createDocumentModel("/", "myDocument", "DocDefaultValue");
        doc.setPropertyValue("dv:complexWithoutDefault/foo", "val1");
        doc.setPropertyValue("dv:complexWithoutDefault/bar", "val2");
        doc = session.createDocument(doc);

        transactionalFeature.nextTransaction();

        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dv:complexWithoutDefault\":{\"foo\":null}}}")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();
        doc = session.getDocument(doc.getRef());
        assertNull(doc.getPropertyValue("dv:complexWithoutDefault/foo"));
        // because of clearComplexPropertyBeforeSet
        assertNull(doc.getPropertyValue("dv:complexWithoutDefault/bar"));
    }

    /*
     * NXP-25280
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server:test-defaultvalue-docTypes.xml")
    public void itCanSetAllSimplePropertyToNullInsideComplexOne() {
        DocumentModel doc = session.createDocumentModel("/", "myDocument", "DocDefaultValue");
        doc.setPropertyValue("dv:complexWithoutDefault/foo", "val1");
        doc.setPropertyValue("dv:complexWithoutDefault/bar", "val2");
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();

        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dv:complexWithoutDefault\":{\"foo\":null,\"bar\":null}}}")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();
        doc = session.getDocument(doc.getRef());
        assertNull(doc.getPropertyValue("dv:complexWithoutDefault/foo"));
        assertNull(doc.getPropertyValue("dv:complexWithoutDefault/bar"));
    }

    /*
     * NXP-25280
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server:test-defaultvalue-docTypes.xml")
    public void itCanSetSimplePropertyToNullAndAnotherOneToNonNullInsideComplexOne() {
        DocumentModel doc = session.createDocumentModel("/", "myDocument", "DocDefaultValue");
        doc.setPropertyValue("dv:complexWithoutDefault/foo", "val1");
        doc.setPropertyValue("dv:complexWithoutDefault/bar", "val2");
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();

        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dv:complexWithoutDefault\":{\"foo\":\"val3\",\"bar\":null}}}")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();
        doc = session.getDocument(doc.getRef());
        assertEquals("val3", doc.getPropertyValue("dv:complexWithoutDefault/foo"));
        assertNull(doc.getPropertyValue("dv:complexWithoutDefault/bar"));
    }

    /*
     * NXP-28298
     */
    @Test
    public void itCanSetArrayPropertyToEmpty() {
        DocumentModel doc = session.createDocumentModel("/", "myDocument", "File");
        doc.setPropertyValue("dc:subjects", new String[] { "foo" });
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();

        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dc:subjects\":[]}}")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode jsonProperties = node.get("properties");
                      assertNotNull(jsonProperties);
                      JsonNode jsonSubjects = jsonProperties.get("dc:subjects");
                      assertNotNull(jsonSubjects);
                      assertTrue(jsonSubjects.isArray());
                      assertTrue(jsonSubjects.isEmpty());
                  });

        // Then the document is updated
        transactionalFeature.nextTransaction();
        doc = session.getDocument(doc.getRef());
        // ArrayProperty returns null whenever is empty or null
        assertNull(doc.getPropertyValue("dc:subjects"));
    }

    /*
     * NXP-28298
     */
    @Test
    public void itCanSetArrayPropertyToNull() {
        DocumentModel doc = session.createDocumentModel("/", "myDocument", "File");
        doc.setPropertyValue("dc:subjects", new String[] { "foo" });
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();

        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dc:subjects\":null}}")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode jsonProperties = node.get("properties");
                      assertNotNull(jsonProperties);
                      JsonNode jsonSubjects = jsonProperties.get("dc:subjects");
                      assertNotNull(jsonSubjects);
                      assertTrue(jsonSubjects.isArray());
                      assertTrue(jsonSubjects.isEmpty());
                  });

        // Then the document is updated
        transactionalFeature.nextTransaction();
        doc = session.getDocument(doc.getRef());
        // ArrayProperty returns null whenever is empty or null
        assertNull(doc.getPropertyValue("dc:subjects"));
    }

    /*
     * NXP-28433
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server:test-defaultvalue-docTypes.xml")
    public void itCanSetArrayPropertyOfComplexToEmpty() {
        DocumentModel doc = session.createDocumentModel("/", "myDocument", "DocDefaultValue");
        doc.setProperty("defaultvalue", "multiComplexWithoutDefault", List.of(Map.of("foo", "val1", "bar", "val2")));
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();

        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dv:multiComplexWithoutDefault\":[]}}")
                  .addHeader("X-NXDocumentProperties", "defaultvalue")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode jsonProperties = node.get("properties");
                      assertNotNull(jsonProperties);
                      JsonNode jsonMultiComplex = jsonProperties.get("dv:multiComplexWithoutDefault");
                      assertNotNull(jsonMultiComplex);
                      assertTrue(jsonMultiComplex.isArray());
                      assertTrue(jsonMultiComplex.isEmpty());
                  });

        // Then the document is updated
        transactionalFeature.nextTransaction();
        doc = session.getDocument(doc.getRef());
        // ListProperty returns an empty List whenever is empty or null
        Serializable multiComplex = doc.getPropertyValue("dv:multiComplexWithoutDefault");
        assertTrue(multiComplex instanceof List);
        assertTrue(((List<?>) multiComplex).isEmpty());
    }

    /*
     * NXP-28433
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server:test-defaultvalue-docTypes.xml")
    public void itCanSetArrayPropertyOfComplexToNull() {
        DocumentModel doc = session.createDocumentModel("/", "myDocument", "DocDefaultValue");
        doc.setProperty("defaultvalue", "multiComplexWithoutDefault", List.of(Map.of("foo", "val1", "bar", "val2")));
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();

        httpClient.buildPutRequest("/id/" + doc.getId())
                  .entity("{\"entity-type\":\"document\",\"properties\":{\"dv:multiComplexWithoutDefault\":null}}")
                  .addHeader("X-NXDocumentProperties", "defaultvalue")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode jsonProperties = node.get("properties");
                      assertNotNull(jsonProperties);
                      JsonNode jsonMultiComplex = jsonProperties.get("dv:multiComplexWithoutDefault");
                      assertNotNull(jsonMultiComplex);
                      assertTrue(jsonMultiComplex.isArray());
                      assertTrue(jsonMultiComplex.isEmpty());
                  });

        // Then the document is updated
        transactionalFeature.nextTransaction();
        doc = session.getDocument(doc.getRef());
        // ListProperty returns an empty List whenever is empty or null
        Serializable multiComplex = doc.getPropertyValue("dv:multiComplexWithoutDefault");
        assertTrue(multiComplex instanceof List);
        assertTrue(((List<?>) multiComplex).isEmpty());
    }

    @Test
    public void iCanCreateADocument() {
        // Given a folder and a Rest Creation request
        DocumentModel folder = RestServerInit.getFolder(0, session);

        String data = "{\"entity-type\": \"document\",\"type\": \"File\",\"name\":\"newName\",\"properties\": {\"dc:title\":\"My title\",\"dc:description\":\" \"}}";

        String id = httpClient.buildPostRequest("/path" + folder.getPathAsString())
                              .entity(data)
                              .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> {
                                  // Then the create document is returned
                                  assertEquals("My title", node.get("title").asText());
                                  assertEquals(" ", node.get("properties").get("dc:description").textValue());
                                  String uid = node.get("uid").asText();
                                  assertTrue(StringUtils.isNotBlank(uid));
                                  return uid;
                              });

        // Then a document is created in the database
        transactionalFeature.nextTransaction();
        DocumentModel doc = session.getDocument(new IdRef(id));
        assertEquals(folder.getPathAsString() + "/newName", doc.getPathAsString());
        assertEquals("My title", doc.getTitle());
        assertEquals("File", doc.getType());
    }

    /**
     * NXP-28349
     */
    @Test
    public void iCanCreateADocumentWithNonExistingField() {
        // Given a folder and a Rest Creation request
        DocumentModel folder = RestServerInit.getFolder(0, session);

        String data = "{\"entity-type\": \"document\",\"type\": \"File\",\"name\":\"newName\",\"properties\": {\"dc:title\":\"My title\",\"note:note\":\"File does not have note\"}}";

        httpClient.buildPostRequest("/path" + folder.getPathAsString())
                  .entity(data)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
    }

    // NXP-30680
    @Test
    @Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-repo-core-types-contrib.xml")
    public void iCantCreateADocumentWithAWrongPropertyType() {
        DocumentModel folder = RestServerInit.getFolder(0, session);

        String data = "{\"entity-type\": \"document\",\"type\": \"MyDocType\",\"name\":\"newName\",\"properties\": {\"my:integer\":\"Some string\"}}";

        httpClient.buildPostRequest("/path" + folder.getPathAsString())
                  .entity(data)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_BAD_REQUEST, status.intValue()));
    }

    // NXP-30052
    @Test
    public void iCantCreateADocumentWithNonExistingType() {
        DocumentModel folder = RestServerInit.getFolder(0, session);

        String data = "{\"entity-type\": \"document\",\"type\": \"Foo\",\"name\":\"newName\",\"properties\": {\"dc:title\":\"Foo\"}}";

        httpClient.buildPostRequest("/path" + folder.getPathAsString())
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> {
                      assertEquals(400, node.get("status").longValue());
                      assertEquals("Type: Foo does not exist", node.get("message").textValue());
                  });
    }

    @Test
    public void iCanDeleteADocument() {
        // Given a document
        DocumentModel doc = RestServerInit.getNote(0, session);

        // When I do a DELETE request
        httpClient.buildDeleteRequest("/path" + doc.getPathAsString())
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        transactionalFeature.nextTransaction();
        // Then the doc is deleted
        assertFalse(session.exists(doc.getRef()));
    }

    @Test
    public void iCanChooseAnotherRepositoryName() {
        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);

        // When i do a GET Request on the note repository
        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then i get a document
                          node -> assertDocumentEqualsNode(note, node));

        // When i do a GET Request on a non existent repository
        httpClient.buildGetRequest("/repo/nonexistentrepo/path" + note.getPathAsString())
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Then i receive a 404
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    @Test
    public void iCanGetTheACLsOnADocumentThroughAdapter() {
        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);

        // When i do a GET Request on the note repository
        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString() + "/@acl")
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then i get a the ACL
                          node -> assertEquals(ACPJsonWriter.ENTITY_TYPE, node.get("entity-type").asText()));
    }

    @Test
    public void iCanGetTheACLsOnADocumentThroughContributor() {
        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);

        // When i do a GET Request on the note repository
        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", ACLJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then i get a the ACL
                          node -> assertEquals("inherited",
                                  node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                      .get("acls")
                                      .get(0)
                                      .get("name")
                                      .textValue()));
    }

    @Test
    public void iCanGetTheThumbnailOfADocumentThroughContributor() {
        // TODO NXP-14793: Improve testing by adding thumbnail conversion
        // Attach a blob
        // Blob blob = new InputStreamBlob(DocumentBrowsingTest.class.getResource(
        // "/test-data/png.png").openStream(), "image/png",
        // null, "logo.png", null);
        // DocumentModel file = RestServerInit.getFile(0, session);
        // file.setPropertyValue("file:content", (Serializable) blob);
        // file = session.saveDocument(file);
        // session.save();
        // ClientResponse response = getResponse(
        // RequestType.GET,
        // "repo/" + file.getRepositoryName() + "/path"
        // + file.getPathAsString(), headers);
        // Then i get an entry for thumbnail
        // assertEquals(SC_OK, response.getStatus());
        // JsonNode node = MAPPER.readTree(response.getEntityInputStream());
        // assertEquals("specificUrl", node.get(RestConstants
        // .CONTRIBUTOR_CTX_PARAMETERS).get("thumbnail").get
        // ("thumbnailUrl").textValue());

        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);

        // When i do a GET Request on the note without any image
        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", ThumbnailJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then i get no result for valid thumbnail url as expected but still
                          // thumbnail entry from the contributor
                          node -> assertNotNull(node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                                    .get("thumbnail")
                                                    .get("url")
                                                    .textValue()));
    }

    /**
     * @since 8.1
     */
    @Test
    public void iCanGetIsADocumentFavorite() {

        Map<String, String> headers = new HashMap<>();
        headers.put(MarshallingConstants.EMBED_ENRICHERS + ".document", FavoritesJsonEnricher.NAME);

        DocumentModel note = RestServerInit.getNote(0, session);

        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertFalse(node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                      .get(FavoritesJsonEnricher.NAME)
                                      .get(FavoritesJsonEnricher.IS_FAVORITE)
                                      .booleanValue());
                  });
        // The above GET will force the creation of the user workspace if it did not exist yet.
        // Force to refresh current transaction context.
        transactionalFeature.nextTransaction();

        FavoritesManager favoritesManager = Framework.getService(FavoritesManager.class);
        favoritesManager.addToFavorites(note, session);

        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertTrue(node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                                 .get(FavoritesJsonEnricher.NAME)
                                                 .get(FavoritesJsonEnricher.IS_FAVORITE)
                                                 .booleanValue()));
    }

    /**
     * @since 8.3
     */
    @Test
    public void iCanGetDocumentTags() {

        Map<String, String> headers = new HashMap<>();
        headers.put(MarshallingConstants.EMBED_ENRICHERS + ".document", TagsJsonEnricher.NAME);

        DocumentModel note = RestServerInit.getNote(0, session);

        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(0,
                          node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS).get(TagsJsonEnricher.NAME).size()));

        // The above GET will force the creation of the user workspace if it did not exist yet.
        // Force to refresh current transaction context.
        transactionalFeature.nextTransaction();

        TagService tagService = Framework.getService(TagService.class);
        tagService.tag(session, note.getId(), "pouet");

        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode tags = node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS).get(TagsJsonEnricher.NAME);
                      if (!tags.isEmpty()) { // XXX NXP-17670 tags not implemented for MongoDB
                          assertEquals(1, tags.size());
                          assertEquals("pouet", tags.get(0).textValue());
                      }
                  });
    }

    /**
     * @since 8.3
     */
    @Test
    public void iCanGetTheCollectionsOfADocument() {

        Map<String, String> headers = new HashMap<>();
        headers.put(MarshallingConstants.EMBED_ENRICHERS + ".document", CollectionsJsonEnricher.NAME);

        DocumentModel note = RestServerInit.getNote(0, session);

        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(0,
                          node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS).get(CollectionsJsonEnricher.NAME).size()));

        // The above GET will force the creation of the user workspace if it did not exist yet.
        // Force to refresh current transaction context.
        transactionalFeature.nextTransaction();

        CollectionManager collectionManager = Framework.getService(CollectionManager.class);
        collectionManager.addToNewCollection("dummyCollection", null, note, session);

        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      ArrayNode collections = (ArrayNode) node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                                              .get(CollectionsJsonEnricher.NAME);
                      assertEquals(1, collections.size());
                      assertEquals("dummyCollection", collections.get(0).get("title").textValue());
                  });
    }

    @Test
    public void iCanGetThePermissionsOnADocumentUnderRetention() {
        // Given an existing doc under retention as an admin
        DocumentModel file = RestServerInit.getFile(0, session);
        Calendar retainUntil = Calendar.getInstance();
        retainUntil.add(Calendar.DAY_OF_MONTH, 5);
        session.makeRecord(file.getRef());
        session.setRetainUntil(file.getRef(), retainUntil, "any comment");
        transactionalFeature.nextTransaction();

        // When i do a GET Request on the doc
        httpClient.buildGetRequest("/repo/" + file.getRepositoryName() + "/path" + file.getPathAsString())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", BasePermissionsJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i get a list of permissions as an admin
                      JsonNode permissions = node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS).get("permissions");
                      assertNotNull(permissions);
                      assertTrue(permissions.isArray());
                      assertFalse(permissions.isEmpty());
                  });
    }

    @Test
    public void iCanGetThePermissionsOnADocumentThroughContributor() {
        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);

        // When i do a GET Request on the note repository
        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", BasePermissionsJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i get a list of permissions
                      JsonNode permissions = node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS).get("permissions");
                      assertNotNull(permissions);
                      assertTrue(permissions.isArray());
                  });
    }

    @Test
    public void iCanGetThePreviewURLThroughContributor() {
        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);

        // When i do a GET Request on the note repository
        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", PreviewJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i get a preview url
                      JsonNode preview = node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS).get("preview");
                      assertNotNull(preview);
                      StringUtils.endsWith(preview.get("url").textValue(), "/default/");
                  });
    }

    @Test
    public void itCanBrowseDocumentWithSpacesInPath() {
        DocumentModel folder = RestServerInit.getFolder(0, session);
        DocumentModel note = session.createDocumentModel(folder.getPathAsString(), "doc with space", "Note");
        note = session.createDocument(note);
        transactionalFeature.nextTransaction();

        // When i do a GET Request on the note repository
        httpClient.buildGetRequest(
                "/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString().replace(" ", "%20"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Then i get a the ACL
                          status -> assertEquals(SC_OK, status.intValue()));

        // When i do a GET Request on the note repository
        httpClient.buildGetRequest("/repo/" + note.getRepositoryName() + "/path" + note.getPathAsString())
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Then i get a the ACL
                          status -> assertEquals(SC_OK, status.intValue()));
    }

    @Test
    public void itCanModifyArrayTypes() throws Exception {
        // Given a document
        DocumentModel note = RestServerInit.getNote(0, session);
        JSONDocumentNode jsonDoc = httpClient.buildGetRequest("/id/" + note.getId())
                                             .addHeader("X-NXDocumentProperties", "dublincore")
                                             .execute(new JSONDocumentNodeHandler());

        // When i do a PUT request on the document with modified data
        jsonDoc.setPropertyValue("dc:title", "New title");
        jsonDoc.setPropertyArray("dc:contributors", "bob");
        httpClient.buildPutRequest("/id/" + note.getId())
                  .entity(jsonDoc.asJson())
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then the document is updated
        transactionalFeature.nextTransaction();
        note = RestServerInit.getNote(0, session);
        assertEquals("New title", note.getTitle());

        List<String> contributors = Arrays.asList((String[]) note.getPropertyValue("dc:contributors"));
        assertTrue(contributors.contains("bob"));
        assertTrue(contributors.contains("Administrator"));
        assertEquals(2, contributors.size());
    }

    // NXP-30846
    @Deploy("org.nuxeo.ecm.platform.restapi.test.test:delivery-doctype.xml")
    @Test
    public void itCanAccessDetachedDocumentACP() {
        DocumentModel doc = session.createDocumentModel("/", "deliv", "Delivery");
        DocumentModel subDoc = session.createDocumentModel("/", "deliv2", "Delivery");
        subDoc = session.createDocument(subDoc);
        doc.setPropertyValue("delivery:docu", subDoc.getId());
        doc = session.createDocument(doc);

        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/id/" + doc.getId())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", ACLJsonEnricher.NAME)
                  .addHeader("properties", "*")
                  .addHeader("fetch.document", "delivery:docy")
                  .addHeader("depth", "max")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      var jsonAssert = JsonAssert.on(response.getEntityString());
                      jsonAssert.has("properties").has("delivery:docu");
                      jsonAssert.has("contextParameters").has("acls");
                  }));
    }

    protected void assertDocumentEqualsNode(DocumentModel expected, JsonNode actual) {
        assertEquals("document", actual.get("entity-type").asText());
        assertEquals(expected.getPathAsString(), actual.get("path").asText());
        assertEquals(expected.getId(), actual.get("uid").asText());
        assertEquals(expected.getTitle(), actual.get("title").asText());
    }
}
