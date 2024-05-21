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

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.restapi.server.jaxrs.QueryObject;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.ChildrenAdapter;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.PageProviderAdapter;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.SearchAdapter;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Test the various ways to query for document lists.
 *
 * @since 5.7.2
 */
@RunWith(FeaturesRunner.class)
@Features(RestServerFeature.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test:pageprovider-test-contrib.xml")
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class DocumentListTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void iCanGetTheChildrenOfADocument() {
        // Given a folder
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When I query for it children
        httpClient.buildGetRequest("/id/" + folder.getId() + "/@" + ChildrenAdapter.NAME)
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get its children as JSON
                          node -> assertEquals(session.getChildren(folder.getRef()).size(),
                                  JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanSearchInFullTextForDocuments() {
        assumeTrue("fulltext search not supported", coreFeature.getStorageConfiguration().supportsFulltextSearch());

        // Given a note with "nuxeo" in its description
        DocumentModel note = RestServerInit.getNote(0, session);
        note.setPropertyValue("dc:description", "nuxeo one platform to rule them all");
        session.saveDocument(note);

        // Waiting for all async events work for indexing content before executing fulltext search
        transactionalFeature.nextTransaction();
        coreFeature.getStorageConfiguration().sleepForFulltext();

        // When I search for "nuxeo"
        httpClient.buildGetRequest("/path/@" + SearchAdapter.NAME)
                  .addQueryParameter("fullText", "nuxeo")
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get the document in the result
                          node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformQueriesOnRepository() {
        // Given a repository, when I perform a query in NXQL on it
        var jsonNodeHandler = new JsonNodeHandler();
        httpClient.buildGetRequest("/" + QueryObject.PATH)
                  .addQueryParameter("query", "SELECT * FROM Document")
                  .executeAndConsume(jsonNodeHandler,
                          // Then I get document listing as result
                          node -> assertEquals(20, JsonNodeHelper.getEntriesSize(node)));

        // Given a repository, when I perform a query in NXQL on it
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/" + QueryObject.NXQL)
                  .addQueryParameter("query", "SELECT * FROM Document")
                  .executeAndConsume(jsonNodeHandler,
                          // Then I get document listing as result
                          node -> assertEquals(20, JsonNodeHelper.getEntriesSize(node)));

        // Given a repository, when I perform a query in NXQL on it
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/" + QueryObject.NXQL)
                  // Given parameters as page size and ordered parameters
                  .addQueryParameter("query", "SELECT * FROM Document WHERE dc:creator = ?")
                  .addQueryParameter("queryParams", "$currentUser")
                  .addQueryParameter("pageSize", "2")
                  .executeAndConsume(jsonNodeHandler,
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformQueriesWithNamedParametersOnRepository() {
        DocumentModel folder = RestServerInit.getFolder(1, session);
        // Given a repository and named parameters, when I perform a query in NXQL on it
        httpClient.buildGetRequest("/" + QueryObject.PATH)
                  .addQueryParameter("query", """
                          SELECT * FROM Document WHERE ecm:parentId = :parentIdVar
                                  AND ecm:mixinType != 'HiddenInNavigation'
                                  AND dc:title IN (:note1,:note2)
                                  AND ecm:isVersion = 0
                                  AND ecm:isTrashed = 0
                          """)
                  .addQueryParameter("note1", "Note 1")
                  .addQueryParameter("note2", "Note 2")
                  .addQueryParameter("parentIdVar", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformQueriesWithNamedParametersOnRepositoryAndSameVariables() {
        DocumentModel folder = RestServerInit.getFolder(1, session);
        // Given a repository and named parameters, when I perform a query in NXQL on it
        httpClient.buildGetRequest("/" + QueryObject.PATH)
                  .addQueryParameter("query", """
                          SELECT * FROM Document WHERE ecm:parentId = :parentIdVar
                                  AND ecm:mixinType != 'HiddenInNavigation'
                                  AND dc:title IN (:title,:title2)
                                  AND ecm:isVersion = 0
                                  AND ecm:isTrashed = 0
                          """)
                  .addQueryParameter("title", "Note 1")
                  .addQueryParameter("title2", "Note 2")
                  .addQueryParameter("parentIdVar", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformPageProviderOnRepository() {
        DocumentModel folder = RestServerInit.getFolder(1, session);
        // Given a repository, when I perform a pageprovider on it
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/TEST_PP")
                  .addQueryParameter("queryParams", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformPageProviderWithNamedParametersOnRepository() {
        DocumentModel folder = RestServerInit.getFolder(1, session);
        // Given a repository, when I perform a pageprovider on it
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/TEST_PP_PARAM")
                  .addQueryParameter("note1", "Note 1")
                  .addQueryParameter("note2", "Note 2")
                  .addQueryParameter("parentIdVar", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    /**
     * @since 7.1
     */
    @Test
    public void iCanPerformPageProviderWithNamedParametersInvalid() {
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderInvalid")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> assertEquals(
                          "Failed to execute query: SELECT * FROM Document where dc:title=:foo ORDER BY dc:title, Lexical Error: Illegal character <:> at offset 38",
                          JsonNodeHelper.getErrorMessage(node)));
    }

    /**
     * @since 7.1
     */
    @Test
    public void iCanPerformPageProviderWithNamedParametersAndDoc() {
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderWithDoc")
                  .addQueryParameter("np:title", "Folder 0")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));
    }

    /**
     * @since 7.1
     */
    @Test
    public void iCanPerformPageProviderWithNamedParametersAndDocInvalid() {
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderWithDocInvalid")
                  .addQueryParameter("np:title", "Folder 0")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> assertEquals(
                          "Failed to execute query: SELECT * FROM Document where dc:title=:foo ORDER BY dc:title, Lexical Error: Illegal character <:> at offset 38",
                          JsonNodeHelper.getErrorMessage(node)));
    }

    /**
     * @since 7.1
     */
    @Test
    public void iCanPerformPageProviderWithNamedParametersInWhereClause() {
        var jsonNodeHandler = new JsonNodeHandler();
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderWithWhereClause")
                  .addQueryParameter("parameter1", "Folder 0")
                  .executeAndConsume(jsonNodeHandler, node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));

        // retry without params
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderWithWhereClause")
                  .executeAndConsume(jsonNodeHandler, node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    /**
     * @since 8.4
     */
    @Test
    public void iCanPerformPageProviderWithNamedParametersWithQuickFilter() {
        JsonNode node = httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderWithQuickFilter")
                                  .addQueryParameter("quickFilters", "testQuickFilter")
                                  .execute(new JsonNodeHandler());
        assertEquals(1, JsonNodeHelper.getEntriesSize(node));
    }

    /**
     * @since 7.1
     */
    @Test
    public void iCanPerformPageProviderWithNamedParametersComplex() {
        var jsonNodeHandler = new JsonNodeHandler();
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderComplex")
                  .addQueryParameter("parameter1", "Folder 0")
                  .addQueryParameter("np:isCheckedIn", Boolean.FALSE.toString())
                  .addQueryParameter("np:dateMin", "2007-01-30 01:02:03+04:00")
                  .addQueryParameter("np:dateMax", "2007-03-23 01:02:03+04:00")
                  .executeAndConsume(jsonNodeHandler, node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));

        // remove filter on dates
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderComplex")
                  .addQueryParameter("parameter1", "Folder 0")
                  .addQueryParameter("np:isCheckedIn", Boolean.FALSE.toString())
                  .executeAndConsume(jsonNodeHandler, node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));

        httpClient.buildGetRequest("/" + QueryObject.PATH + "/namedParamProviderComplex")
                  .addQueryParameter("np:isCheckedIn", Boolean.FALSE.toString())
                  .executeAndConsume(jsonNodeHandler, node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void itCanGetAdapterForRootDocument() {
        // Given the root document
        DocumentModel rootDocument = session.getRootDocument();

        // When i ask for an adapter
        httpClient.buildGetRequest("/path" + rootDocument.getPathAsString() + "@children")
                  .executeAndConsume(new JsonNodeHandler(),
                          // The it return a response
                          node -> assertEquals(session.getChildren(rootDocument.getRef()).size(),
                                  JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanUseAPageProvider() {
        // Given a note with "nuxeo" in its description
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When I search for "nuxeo"
        httpClient.buildGetRequest("/path" + folder.getPathAsString() + "/@" + PageProviderAdapter.NAME + "/TEST_PP")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanDeleteAListOfDocuments() {
        // Given two notes
        DocumentModel note1 = RestServerInit.getNote(1, session);
        DocumentModel folder0 = RestServerInit.getFolder(0, session);

        // When i call a bulk delete
        httpClient.buildDeleteRequest("/bulk")
                  .addMatrixParameter("id", note1.getId())
                  .addMatrixParameter("id", folder0.getId())
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Then the documents are removed from repository
        transactionalFeature.nextTransaction();

        assertFalse(session.exists(note1.getRef()));
        assertFalse(session.exists(folder0.getRef()));
    }

    @Test
    public void iCanUpdateDocumentLists() {
        // Given two notes
        DocumentModel note1 = RestServerInit.getNote(1, session);
        DocumentModel note2 = RestServerInit.getNote(2, session);

        String data = "{\"entity-type\":\"document\"," + "\"type\":\"Note\"," + "\"properties\":{"
                + "    \"dc:description\":\"bulk description\"" + "  }" + "}";

        // When i call a bulk update
        httpClient.buildPutRequest("/bulk")
                  .addMatrixParameter("id", note1.getId())
                  .addMatrixParameter("id", note2.getId())
                  .entity(data)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
        // Then the documents are updated accordingly
        transactionalFeature.nextTransaction();
        for (int i : new int[] { 1, 2 }) {
            note1 = RestServerInit.getNote(i, session);
            assertEquals("bulk description", note1.getPropertyValue("dc:description"));
        }
    }

    /**
     * @since 8.10
     */
    @Test
    public void iCanPerformPageProviderWithDefinitionDefaultSorting() {
        // Given a repository, when I perform a pageprovider on it with
        // default sorting in its definition on dc:title desc
        httpClient.buildGetRequest("/" + QueryObject.PATH + "/TEST_NOTE_PP_WITH_TITLE_ORDER")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then I get document listing as result
                      List<JsonNode> noteNodes = JsonNodeHelper.getEntries(node);
                      assertEquals(RestServerInit.MAX_NOTE, noteNodes.size());
                      for (int i = 0; i < noteNodes.size(); i++) {
                          assertEquals("Note " + (RestServerInit.MAX_NOTE - (i + 1)),
                                  noteNodes.get(i).get("title").textValue());
                      }
                  });
    }

}
