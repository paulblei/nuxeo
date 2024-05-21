/*
 * (C) Copyright 2018-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *       Kevin Leturc <kleturc@nuxeo.com>
 *       Nuno Cunha <ncunha@nuxeo.com>
 */

package org.nuxeo.ecm.restapi.server.jaxrs.comment;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.comment.CommentUtils.createUser;
import static org.nuxeo.ecm.platform.comment.CommentUtils.newComment;
import static org.nuxeo.ecm.platform.comment.CommentUtils.newExternalComment;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_AUTHOR_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_CREATION_DATE_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_ENTITY_TYPE;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_ID_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_LAST_REPLY_DATE_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_MODIFICATION_DATE_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_NUMBER_OF_REPLIES_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_PARENT_ID_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_PERMISSIONS_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_TEXT_FIELD;
import static org.nuxeo.ecm.platform.comment.api.ExternalEntityConstants.EXTERNAL_ENTITY;
import static org.nuxeo.ecm.platform.comment.api.ExternalEntityConstants.EXTERNAL_ENTITY_ID_FIELD;
import static org.nuxeo.ecm.platform.comment.api.ExternalEntityConstants.EXTERNAL_ENTITY_ORIGIN_FIELD;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.security.PermissionProvider;
import org.nuxeo.ecm.core.io.registry.MarshallerHelper;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext.CtxBuilder;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.comment.impl.CommentJsonWriter;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;

/**
 * @since 10.3
 */
@RunWith(FeaturesRunner.class)
@Features(CommentAdapterFeature.class)
public abstract class AbstractCommentAdapterTest {

    protected static final String JDOE = "jdoe";

    @Inject
    protected CommentManager commentManager;

    @Inject
    protected CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    protected DocumentModel file;

    @Before
    public void setup() {
        DocumentModel domain = session.createDocumentModel("/", "testDomain", "Domain");
        session.createDocument(domain);
        file = session.createDocumentModel("/testDomain", "testDoc", "File");
        file = session.createDocument(file);
        transactionalFeature.nextTransaction();
    }

    @Test
    public void testCreateComment() throws IOException {
        Comment comment = newComment(file.getId(), "Some text");

        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());

        httpClient.buildPostRequest("/id/" + file.getId() + "/@comment")
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(session.getPrincipal().getName(), node.get(COMMENT_AUTHOR_FIELD).textValue());
                      assertEquals("Some text", node.get(COMMENT_TEXT_FIELD).textValue());
                  });
    }

    /**
     * @since 11.1
     */
    @Test
    public void testCreateCommentSetCorrectAuthor() throws IOException {
        Comment comment = newComment(file.getId());
        String fakeAuthor = "fakeAuthor";
        comment.setAuthor(fakeAuthor);

        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());

        httpClient.buildPostRequest("/id/" + file.getId() + "/@comment")
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      String author = node.get(COMMENT_AUTHOR_FIELD).textValue();
                      assertNotEquals(fakeAuthor, author);
                      assertEquals(session.getPrincipal().getName(), author);
                  });
    }

    @Test
    public void testCreateCommentWithoutCreationDate() throws IOException {
        Comment comment = newComment(file.getId());
        comment.setCreationDate(null);

        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());

        httpClient.buildPostRequest("/id/" + file.getId() + "/@comment")
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(session.getPrincipal().getName(), node.get(COMMENT_AUTHOR_FIELD).textValue());
                      assertEquals(comment.getText(), node.get(COMMENT_TEXT_FIELD).textValue());
                      assertNotNull(node.get(COMMENT_CREATION_DATE_FIELD).textValue());
                  });
    }

    @Test
    public void testGetCommentsForNonExistingDocument() {
        httpClient.buildGetRequest("/id/nonExistingDocId/@comment")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    @Test
    public void testGetComments() {
        Comment comment1 = commentManager.createComment(session, newComment(file.getId()));
        Comment comment2 = commentManager.createComment(session, newComment(file.getId()));
        Comment comment3 = commentManager.createComment(session, newComment(file.getId()));
        Comment comment4 = commentManager.createComment(session, newComment(file.getId()));
        Comment comment5 = commentManager.createComment(session, newComment(file.getId()));

        String comment1Id = comment1.getId();
        String comment2Id = comment2.getId();
        String comment3Id = comment3.getId();
        String comment4Id = comment4.getId();
        String comment5Id = comment5.getId();

        transactionalFeature.nextTransaction();

        // test without pagination
        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode entries = node.get("entries");
                      assertEquals(5, entries.size());
                      Set<String> expectedIds = new HashSet<>(
                              List.of(comment1Id, comment2Id, comment3Id, comment4Id, comment5Id));
                      Set<String> actualIds = new HashSet<>(entries.findValuesAsText("id"));
                      assertEquals(expectedIds, actualIds);
                  });

        // test with pagination
        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment")
                  .addQueryParameter("currentPageIndex", "0")
                  .addQueryParameter("pageSize", "2")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode entries = node.get("entries");
                      Set<String> expectedIds = new HashSet<>(List.of(comment5Id, comment4Id));
                      Set<String> actualIds = new HashSet<>(entries.findValuesAsText("id"));
                      assertEquals(expectedIds, actualIds);
                  });

        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment")
                  .addQueryParameter("currentPageIndex", "1")
                  .addQueryParameter("pageSize", "2")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode entries = node.get("entries");
                      Set<String> expectedIds = new HashSet<>(List.of(comment3Id, comment2Id));
                      Set<String> actualIds = new HashSet<>(entries.findValuesAsText("id"));
                      assertEquals(expectedIds, actualIds);
                  });

        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment")
                  .addQueryParameter("currentPageIndex", "2")
                  .addQueryParameter("pageSize", "2")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode entries = node.get("entries");
                      Set<String> expectedIds = new HashSet<>(List.of(comment1Id));
                      Set<String> actualIds = new HashSet<>(entries.findValuesAsText("id"));
                      assertEquals(expectedIds, actualIds);
                  });
    }

    @Test
    public void testGetCommentWithNonExistingId() {
        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment/nonExistingId")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    @Test
    public void testGetComment() {
        Comment comment = commentManager.createComment(session, newComment(file.getId(), "Some text"));
        String commentId = comment.getId();
        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals(COMMENT_ENTITY_TYPE, node.get("entity-type").asText());
                      assertEquals(comment.getId(), node.get(COMMENT_ID_FIELD).textValue());
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(comment.getAuthor(), node.get(COMMENT_AUTHOR_FIELD).textValue());
                      assertEquals("Some text", node.get(COMMENT_TEXT_FIELD).textValue());
                      assertEquals(comment.getCreationDate().toString(),
                              node.get(COMMENT_CREATION_DATE_FIELD).textValue());

                      // Get permissions
                      Set<String> grantedPermissions = new HashSet<>(
                              session.filterGrantedPermissions(session.getPrincipal(), file.getRef(),
                                      List.of(Framework.getService(PermissionProvider.class).getPermissions())));
                      Set<String> permissions = StreamSupport.stream(node.get(COMMENT_PERMISSIONS_FIELD).spliterator(),
                              false).map(JsonNode::textValue).collect(Collectors.toSet());

                      assertEquals(grantedPermissions, permissions);
                  });
    }

    @Test
    public void testGetCommentWithoutRepliesUsingRepliesFetcher() {
        Comment comment = commentManager.createComment(session, newComment(file.getId(), "Some text"));
        String commentId = comment.getId();
        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .addQueryParameter("fetch." + COMMENT_ENTITY_TYPE, CommentJsonWriter.FETCH_REPLIES_SUMMARY)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals(COMMENT_ENTITY_TYPE, node.get("entity-type").asText());
                      assertEquals(comment.getId(), node.get(COMMENT_ID_FIELD).textValue());
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(comment.getAuthor(), node.get(COMMENT_AUTHOR_FIELD).textValue());
                      assertEquals("Some text", node.get(COMMENT_TEXT_FIELD).textValue());
                      assertEquals(comment.getCreationDate().toString(),
                              node.get(COMMENT_CREATION_DATE_FIELD).textValue());
                      assertEquals(0, node.get(COMMENT_NUMBER_OF_REPLIES_FIELD).intValue());
                      assertFalse(node.has(COMMENT_LAST_REPLY_DATE_FIELD));
                  });
    }

    @Test
    public void testGetCommentWithRepliesUsingRepliesFetcher() {
        Comment comment = commentManager.createComment(session, newComment(file.getId(), "Some text"));
        String commentId = comment.getId();
        transactionalFeature.nextTransaction();

        commentManager.createComment(session, newComment(commentId));
        commentManager.createComment(session, newComment(commentId));
        Comment reply3 = commentManager.createComment(session, newComment(commentId));
        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .addQueryParameter("fetch." + COMMENT_ENTITY_TYPE, CommentJsonWriter.FETCH_REPLIES_SUMMARY)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals(COMMENT_ENTITY_TYPE, node.get("entity-type").asText());
                      assertEquals(comment.getId(), node.get(COMMENT_ID_FIELD).textValue());
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(comment.getAuthor(), node.get(COMMENT_AUTHOR_FIELD).textValue());
                      assertEquals("Some text", node.get(COMMENT_TEXT_FIELD).textValue());
                      assertEquals(comment.getCreationDate().toString(),
                              node.get(COMMENT_CREATION_DATE_FIELD).textValue());
                      assertEquals(3, node.get(COMMENT_NUMBER_OF_REPLIES_FIELD).intValue());
                      assertTrue(node.has(COMMENT_LAST_REPLY_DATE_FIELD));
                      assertEquals(reply3.getCreationDate().toString(),
                              node.get(COMMENT_LAST_REPLY_DATE_FIELD).textValue());
                  });
    }

    @Test
    public void testUpdateComment() throws IOException {
        Comment comment = commentManager.createComment(session, newComment(file.getId()));
        String author = comment.getAuthor();
        String commentId = comment.getId();
        transactionalFeature.nextTransaction();

        comment.setText("And now I update it");
        comment.setAuthor("fakeAuthor");
        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());

        httpClient.buildPutRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .entity(jsonComment)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        transactionalFeature.nextTransaction();
        Comment updatedComment = commentManager.getComment(session, commentId);
        assertEquals("And now I update it", updatedComment.getText());
        assertEquals(author, updatedComment.getAuthor());
    }

    @Test
    public void testUpdateCommentWithoutModificationDate() throws IOException {
        Comment comment = commentManager.createComment(session, newComment(file.getId()));
        String commentId = comment.getId();
        transactionalFeature.nextTransaction();

        comment.setText("And now I update it");
        comment.setModificationDate(null);
        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());

        httpClient.buildPutRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals("And now I update it", node.get(COMMENT_TEXT_FIELD).textValue());
                      assertNotNull(node.get(COMMENT_MODIFICATION_DATE_FIELD).textValue());
                  });
    }

    /*
     * NXP-28484
     */
    @Test
    public void testUpdateCommentWithPartialData() {
        var comment = commentManager.createComment(session,
                newExternalComment(file.getId(), "an-id", "<entity/>", "Some text"));
        String commentId = comment.getId();
        transactionalFeature.nextTransaction();

        String jsonComment = String.format("{\"entity-type\":\"%s\",\"%s\":\"And now I update it\"}",
                COMMENT_ENTITY_TYPE, COMMENT_TEXT_FIELD);

        httpClient.buildPutRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals("And now I update it", node.get(COMMENT_TEXT_FIELD).textValue());
                      assertEquals(commentId, node.get(COMMENT_ID_FIELD).textValue());
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals("an-id", node.get(EXTERNAL_ENTITY_ID_FIELD).textValue());
                      assertEquals("<entity/>", node.get(EXTERNAL_ENTITY).textValue());
                      assertEquals("Test", node.get(EXTERNAL_ENTITY_ORIGIN_FIELD).textValue());
                  });
    }

    /*
     * NXP-28483
     */
    @Test
    public void testUpdateCommentWithRegularUser() throws IOException {
        // create jdoe user as a regular user
        createUser(JDOE);
        // re-compute read acls
        transactionalFeature.nextTransaction();

        // use rest for creation in order to have the correct author
        Comment comment = newComment(file.getId());
        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());
        String commentId = httpClient.buildPostRequest("/id/" + file.getId() + "/@comment")
                                     .credentials(JDOE, JDOE)
                                     .entity(jsonComment)
                                     .executeAndThen(new JsonNodeHandler(SC_CREATED),
                                             node -> node.get(COMMENT_ID_FIELD).textValue());

        // now update the comment
        comment.setText("And now I update it");
        jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());
        httpClient.buildPutRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .credentials(JDOE, JDOE)
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(),
                          // assert the response
                          node -> assertEquals("And now I update it", node.get(COMMENT_TEXT_FIELD).textValue()));

        transactionalFeature.nextTransaction();
        // assert DB was updated
        Comment updatedComment = commentManager.getComment(session, commentId);
        assertEquals("And now I update it", updatedComment.getText());
    }

    @Test
    public void testDeleteCommentWithNonExistingId() {
        httpClient.buildDeleteRequest("/id/" + file.getId() + "/@comment/nonExistingId")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    @Test
    public void testDeleteComment() {
        Comment comment = commentManager.createComment(session, newComment(file.getId()));
        String commentId = comment.getId();
        transactionalFeature.nextTransaction();

        assertNotNull(commentManager.getComment(session, commentId));

        httpClient.buildDeleteRequest("/id/" + file.getId() + "/@comment/" + commentId)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        transactionalFeature.nextTransaction();
        assertFalse(session.exists(new IdRef(commentId)));
    }

    @Test
    public void testGetExternalComment() {
        String entityId = "foo";
        String entity = "<entity></entity>";

        Comment comment = newExternalComment(file.getId(), entityId, entity, "Some text");
        comment = commentManager.createComment(session, comment);
        transactionalFeature.nextTransaction();

        String commentId = comment.getId();
        httpClient.buildGetRequest("/id/" + file.getId() + "/@comment/external/" + entityId)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals(COMMENT_ENTITY_TYPE, node.get("entity-type").asText());
                      assertEquals(commentId, node.get(COMMENT_ID_FIELD).textValue());
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(entityId, node.get(EXTERNAL_ENTITY_ID_FIELD).textValue());
                      assertEquals("Test", node.get(EXTERNAL_ENTITY_ORIGIN_FIELD).textValue());
                      assertEquals(entity, node.get(EXTERNAL_ENTITY).textValue());
                  });
    }

    @Test
    public void testUpdateExternalComment() throws IOException {
        transactionalFeature.nextTransaction();
        String entityId = "foo";
        String author = "toto";

        Comment comment = newExternalComment(file.getId(), entityId);
        comment.setAuthor(author);
        comment = commentManager.createComment(session, comment);

        transactionalFeature.nextTransaction();

        comment.setAuthor("titi");
        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());

        httpClient.buildPutRequest("/id/" + file.getId() + "/@comment/external/" + entityId)
                  .entity(jsonComment)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        transactionalFeature.nextTransaction();
        Comment updatedComment = commentManager.getExternalComment(session, file.getId(), entityId);
        // Author should not be modified
        assertEquals(author, updatedComment.getAuthor());
    }

    /*
     * NXP-28483
     */
    @Test
    public void testUpdateExternalCommentWithRegularUser() throws IOException {
        // create jdoe user as a regular user
        createUser(JDOE);
        // re-compute read acls
        transactionalFeature.nextTransaction();

        String entityId = "foo";
        // use rest for creation in order to have the correct author
        var comment = newExternalComment(file.getId(), entityId, "<entity/>", "Some text");
        String jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());
        httpClient.buildPostRequest("/id/" + file.getId() + "/@comment")
                  .credentials(JDOE, JDOE)
                  .entity(jsonComment)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // now update the comment
        comment.setText("And now I update it");
        jsonComment = MarshallerHelper.objectToJson(comment, CtxBuilder.session(session).get());
        httpClient.buildPutRequest("/id/" + file.getId() + "/@comment/external/" + entityId)
                  .credentials(JDOE, JDOE)
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(),
                          // assert the response
                          node -> assertEquals("And now I update it", node.get(COMMENT_TEXT_FIELD).textValue()));

        transactionalFeature.nextTransaction();
        // assert DB was updated
        var updatedComment = commentManager.getExternalComment(session, file.getId(), entityId);
        assertEquals("And now I update it", updatedComment.getText());
    }

    @Test
    public void testDeleteExternalComment() {
        String entityId = "foo";
        var comment = commentManager.createComment(session, newExternalComment(file.getId(), entityId));

        transactionalFeature.nextTransaction();

        assertTrue(session.exists(new IdRef(comment.getId())));

        httpClient.buildDeleteRequest("/id/" + file.getId() + "/@comment/external/" + entityId)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
        transactionalFeature.nextTransaction();
        assertFalse(session.exists(new IdRef(comment.getId())));
    }

}
