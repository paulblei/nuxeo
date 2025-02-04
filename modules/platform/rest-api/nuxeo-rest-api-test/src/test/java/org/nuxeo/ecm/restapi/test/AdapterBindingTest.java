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

import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.adapter.BusinessBeanAdapter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.BOAdapter;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @since 5.7.2
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class AdapterBindingTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void iCanGetAnAdapter() {

        // Given a note
        DocumentModel note = RestServerInit.getNote(1, session);

        // When i browse the adapter
        httpClient.buildGetRequest("/id/" + note.getId() + "/@" + BOAdapter.NAME + "/BusinessBeanAdapter")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i receive a formatted response
                      assertEquals("BusinessBeanAdapter", node.get("entity-type").asText());
                      assertEquals(note.getPropertyValue("note:note"), node.get("value").get("note").asText());
                  });
    }

    @Test
    public void iCanSaveAnAdapter() {
        // Given a note and a modified business object representation
        DocumentModel note = RestServerInit.getNote(1, session);
        String ba = """
                {
                  "entity-type": "BusinessBeanAdapter",
                  "value": {
                    "id": "%s",
                    "type": "Note",
                    "note": "Note 1",
                    "title": "Note 1",
                    "description": "description"
                  }
                }
                """.formatted(note.getId());
        assertTrue(StringUtils.isBlank((String) note.getPropertyValue("dc:description")));

        // When i do a put request on it
        httpClient.buildPutRequest("/id/" + note.getId() + "/@" + BOAdapter.NAME + "/BusinessBeanAdapter")
                  .entity(ba)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then it modifies the description
        transactionalFeature.nextTransaction();
        note = session.getDocument(note.getRef());
        assertEquals("description", note.getAdapter(BusinessBeanAdapter.class).getDescription());
    }

    @Test
    public void iCanCreateAnAdapter() {
        // Given a note and a modified business object representation
        DocumentModel folder = RestServerInit.getFolder(0, session);
        String ba = """
                {
                  "entity-type": "BusinessBeanAdapter",
                  "value": {
                    "type": "Note",
                    "note": "Note 1",
                    "title": "Note 1",
                    "description": "description"
                  }
                }
                """;
        assertTrue(session.getChildren(folder.getRef()).isEmpty());

        // When i do a post request on it
        httpClient.buildPostRequest("/id/" + folder.getId() + "/@" + BOAdapter.NAME + "/BusinessBeanAdapter/note2")
                  .entity(ba)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then it modifies the description
        transactionalFeature.nextTransaction();
        assertFalse(session.getChildren(folder.getRef()).isEmpty());
    }

    @Test
    public void iCanGetAdapterOnDocumentLists() {
        // Given a folder
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When i adapt the children of the folder with a BusinessBeanAdapter
        httpClient.buildGetRequest("/id/" + folder.getId() + "/@children/@" + BOAdapter.NAME + "/BusinessBeanAdapter")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i receive a list of businessBeanAdapter
                      assertEquals("adapters", node.get("entity-type").asText());
                      ArrayNode entries = (ArrayNode) node.get("entries");
                      DocumentModelList children = session.getChildren(folder.getRef());
                      assertEquals(children.size(), entries.size());

                      JsonNode jsonNote = entries.get(0);
                      assertEquals("BusinessBeanAdapter", jsonNote.get("entity-type").asText());
                      assertEquals("Note", jsonNote.get("value").get("type").asText());
                  });

    }

    @Test
    public void adapterListArePaginated() {
        // Given a folder
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When i adapt the children of the folder with a BusinessBeanAdapter
        httpClient.buildGetRequest("/id/" + folder.getId() + "/@children/@" + BOAdapter.NAME + "/BusinessBeanAdapter")
                  .addQueryParameter("currentPageIndex", "1")
                  .addQueryParameter("pageSize", "2")
                  .addQueryParameter("sortBy", "dc:title")
                  .addQueryParameter("sortOrder", "DESC")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i receive a list of businessBeanAdapter
                      assertEquals("adapters", node.get("entity-type").asText());
                      assertTrue(node.get("isPaginable").booleanValue());
                      assertEquals(2, node.get("entries").size());

                      JsonNode node1 = node.get("entries").get(0);
                      JsonNode node2 = node.get("entries").get(1);
                      String title1 = node1.get("value").get("title").asText();
                      String title2 = node2.get("value").get("title").asText();
                      assertTrue(title1.compareTo(title2) > 0);
                  });

        // same with multiple sorts
        httpClient.buildGetRequest("/id/" + folder.getId() + "/@children/@" + BOAdapter.NAME + "/BusinessBeanAdapter")
                  .addQueryParameter("currentPageIndex", "1")
                  .addQueryParameter("pageSize", "2")
                  .addQueryParameter("sortBy", "dc:description,dc:title")
                  .addQueryParameter("sortOrder", "asc,desc")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then i receive a list of businessBeanAdapter
                      assertEquals("adapters", node.get("entity-type").asText());
                      assertTrue(node.get("isPaginable").booleanValue());
                      assertEquals(2, node.get("entries").size());

                      JsonNode node1 = node.get("entries").get(0);
                      JsonNode node2 = node.get("entries").get(1);
                      String title1 = node1.get("value").get("title").asText();
                      String title2 = node2.get("value").get("title").asText();
                      assertTrue(title1.compareTo(title2) > 0);
                  });
    }
}
