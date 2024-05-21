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
 *     Funsho David
 */

package org.nuxeo.ecm.restapi.server.jaxrs.comment;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.comment.CommentUtils.createUser;
import static org.nuxeo.ecm.platform.comment.CommentUtils.newAnnotation;
import static org.nuxeo.ecm.platform.comment.CommentUtils.newComment;
import static org.nuxeo.ecm.platform.comment.CommentUtils.newExternalAnnotation;
import static org.nuxeo.ecm.platform.comment.api.AnnotationConstants.ANNOTATION_ENTITY_TYPE;
import static org.nuxeo.ecm.platform.comment.api.AnnotationConstants.ANNOTATION_PERMISSIONS_FIELD;
import static org.nuxeo.ecm.platform.comment.api.AnnotationConstants.ANNOTATION_XPATH_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_ID_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_PARENT_ID_FIELD;
import static org.nuxeo.ecm.platform.comment.api.CommentConstants.COMMENT_TEXT_FIELD;
import static org.nuxeo.ecm.platform.comment.api.ExternalEntityConstants.EXTERNAL_ENTITY;
import static org.nuxeo.ecm.platform.comment.api.ExternalEntityConstants.EXTERNAL_ENTITY_ID_FIELD;
import static org.nuxeo.ecm.platform.comment.api.ExternalEntityConstants.EXTERNAL_ENTITY_ORIGIN_FIELD;
import static org.nuxeo.ecm.restapi.server.jaxrs.comment.AbstractCommentAdapterTest.JDOE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

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
import org.nuxeo.ecm.platform.comment.api.Annotation;
import org.nuxeo.ecm.platform.comment.api.AnnotationService;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 10.1
 */
@RunWith(FeaturesRunner.class)
@Features(CommentAdapterFeature.class)
public abstract class AbstractAnnotationAdapterTest {

    @Inject
    protected AnnotationService annotationService;

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
    public void testCreateAnnotation() throws IOException {
        String xpath = "file:content";
        var annotation = newAnnotation(file.getId(), xpath);

        String jsonAnnotation = MarshallerHelper.objectToJson(annotation, CtxBuilder.session(session).get());

        httpClient.buildPostRequest("/id/" + file.getId() + "/@annotation")
                  .entity(jsonAnnotation)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(xpath, node.get(ANNOTATION_XPATH_FIELD).textValue());
                  });
    }

    @Test
    public void testGetAnnotation() {
        String xpath = "files:files/0/file";
        var annotation = annotationService.createAnnotation(session, newAnnotation(file.getId(), xpath));
        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/id/" + file.getId() + "/@annotation/" + annotation.getId())
                  .executeAndConsume(new JsonNodeHandler(), node -> {

                      assertEquals(ANNOTATION_ENTITY_TYPE, node.get("entity-type").asText());
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(xpath, node.get(ANNOTATION_XPATH_FIELD).textValue());

                      // Get permissions
                      Set<String> grantedPermissions = new HashSet<>(
                              session.filterGrantedPermissions(session.getPrincipal(), file.getRef(),
                                      List.of(Framework.getService(PermissionProvider.class).getPermissions())));
                      Set<String> permissions = StreamSupport.stream(
                              node.get(ANNOTATION_PERMISSIONS_FIELD).spliterator(), false)
                                                             .map(JsonNode::textValue)
                                                             .collect(Collectors.toSet());

                      assertEquals(grantedPermissions, permissions);
                  });
    }

    @Test
    public void testUpdateAnnotation() throws IOException {
        String xpath = "file:content";
        var annotation = annotationService.createAnnotation(session, newAnnotation(file.getId(), xpath));

        transactionalFeature.nextTransaction();

        assertNull(annotation.getText());
        annotation.setText("test");
        annotation.setAuthor("fakeAuthor");
        String jsonAnnotation = MarshallerHelper.objectToJson(annotation, CtxBuilder.session(session).get());

        httpClient.buildPutRequest("/id/" + file.getId() + "/@annotation/" + annotation.getId())
                  .entity(jsonAnnotation)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        transactionalFeature.nextTransaction();

        Annotation updatedAnnotation = annotationService.getAnnotation(session, annotation.getId());

        assertEquals("test", updatedAnnotation.getText());
        assertEquals(session.getPrincipal().getName(), updatedAnnotation.getAuthor());
    }

    /*
     * NXP-28483
     */
    @Test
    public void testUpdateAnnotationWithRegularUser() throws IOException {
        // create jdoe user as a regular user
        createUser(JDOE);
        // re-compute read acls
        transactionalFeature.nextTransaction();

        // use rest for creation in order to have the correct author
        var annotation = newAnnotation(file.getId(), "file:content", "Some text");
        String jsonComment = MarshallerHelper.objectToJson(annotation, CtxBuilder.session(session).get());
        String annotationId = httpClient.buildPostRequest("/id/" + file.getId() + "/@annotation")
                                        .credentials(JDOE, JDOE)
                                        .entity(jsonComment)
                                        .executeAndThen(new JsonNodeHandler(SC_CREATED),
                                                node -> node.get(COMMENT_ID_FIELD).textValue());

        // now update the annotation
        annotation.setText("And now I update it");
        jsonComment = MarshallerHelper.objectToJson(annotation, CtxBuilder.session(session).get());
        httpClient.buildPutRequest("/id/" + file.getId() + "/@annotation/" + annotationId)
                  .credentials(JDOE, JDOE)
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // assert the response
                      assertEquals("And now I update it", node.get(COMMENT_TEXT_FIELD).textValue());
                      transactionalFeature.nextTransaction();
                      // assert DB was updated
                      var updatedAnnotation = annotationService.getAnnotation(session, annotationId);
                      assertEquals("And now I update it", updatedAnnotation.getText());
                  });
    }

    @Test
    public void testDeleteAnnotation() {
        String xpath = "files:files/0/file";
        var annotation = annotationService.createAnnotation(session, newAnnotation(file.getId(), xpath));
        transactionalFeature.nextTransaction();

        assertTrue(session.exists(new IdRef(annotation.getId())));

        httpClient.buildDeleteRequest("/id/" + file.getId() + "/@annotation/" + annotation.getId())
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        transactionalFeature.nextTransaction();
        assertFalse(session.exists(new IdRef(annotation.getId())));
    }

    @Test
    public void testSearchAnnotations() {
        DocumentModel file1 = session.createDocumentModel("/testDomain", "testDoc1", "File");
        file1 = session.createDocument(file1);
        DocumentModel file2 = session.createDocumentModel("/testDomain", "testDoc2", "File");
        file2 = session.createDocument(file2);

        String xpath1 = "files:files/0/file";
        String xpath2 = "files:files/1/file";
        String xpath3 = "file:content";
        var annotation1 = annotationService.createAnnotation(session, newAnnotation(file1.getId(), xpath1));
        var annotation2 = annotationService.createAnnotation(session, newAnnotation(file1.getId(), xpath2));
        var annotation3 = annotationService.createAnnotation(session, newAnnotation(file1.getId(), xpath2));
        var annotation4 = annotationService.createAnnotation(session, newAnnotation(file2.getId(), xpath3));
        var annotation5 = annotationService.createAnnotation(session, newAnnotation(file2.getId(), xpath3));
        transactionalFeature.nextTransaction();

        JsonNode node1 = httpClient.buildGetRequest("/id/" + file1.getId() + "/@annotation")
                                   .addQueryParameter(ANNOTATION_XPATH_FIELD, xpath1)
                                   .executeAndThen(new JsonNodeHandler(), node -> node.get("entries"));
        JsonNode node2 = httpClient.buildGetRequest("/id/" + file1.getId() + "/@annotation")
                                   .addQueryParameter(ANNOTATION_XPATH_FIELD, xpath2)
                                   .executeAndThen(new JsonNodeHandler(), node -> node.get("entries"));
        JsonNode node3 = httpClient.buildGetRequest("/id/" + file2.getId() + "/@annotation")
                                   .addQueryParameter(ANNOTATION_XPATH_FIELD, xpath3)
                                   .executeAndThen(new JsonNodeHandler(), node -> node.get("entries"));

        assertEquals(1, node1.size());
        assertEquals(2, node2.size());
        assertEquals(2, node3.size());

        assertEquals(annotation1.getId(), node1.get(0).get("id").textValue());

        List<String> node2List = List.of(node2.get(0).get("id").textValue(), node2.get(1).get("id").textValue());
        assertTrue(node2List.contains(annotation2.getId()));
        assertTrue(node2List.contains(annotation3.getId()));
        List<String> node3List = List.of(node3.get(0).get("id").textValue(), node3.get(1).get("id").textValue());
        assertTrue(node3List.contains(annotation4.getId()));
        assertTrue(node3List.contains(annotation5.getId()));
    }

    @Test
    public void testGetExternalAnnotation() {
        String xpath = "files:files/0/file";
        String entityId = "foo";
        String entity = "<entity></entity>";
        annotationService.createAnnotation(session, newExternalAnnotation(file.getId(), xpath, entityId, entity));
        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest("/id/" + file.getId() + "/@annotation/external/" + entityId)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals(ANNOTATION_ENTITY_TYPE, node.get("entity-type").asText());
                      assertEquals(entityId, node.get(EXTERNAL_ENTITY_ID_FIELD).textValue());
                      assertEquals("Test", node.get(EXTERNAL_ENTITY_ORIGIN_FIELD).textValue());
                      assertEquals(entity, node.get(EXTERNAL_ENTITY).textValue());
                      assertEquals(file.getId(), node.get(COMMENT_PARENT_ID_FIELD).textValue());
                      assertEquals(xpath, node.get(ANNOTATION_XPATH_FIELD).textValue());
                  });
    }

    @Test
    public void testUpdateExternalAnnotation() throws IOException {
        String xpath = "file:content";
        String entityId = "foo";
        String author = "toto";
        Annotation annotation = newExternalAnnotation(file.getId(), xpath, entityId);
        annotation.setAuthor(author);
        annotation = annotationService.createAnnotation(session, annotation);

        transactionalFeature.nextTransaction();

        annotation.setAuthor("titi");
        String jsonAnnotation = MarshallerHelper.objectToJson(annotation, CtxBuilder.session(session).get());

        httpClient.buildPutRequest("/id/" + file.getId() + "/@annotation/external/" + entityId)
                  .entity(jsonAnnotation)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        transactionalFeature.nextTransaction();
        Annotation updatedAnnotation = annotationService.getExternalAnnotation(session, file.getId(), entityId);
        assertEquals(author, updatedAnnotation.getAuthor());
    }

    /*
     * NXP-28483
     */
    @Test
    public void testUpdateExternalAnnotationWithRegularUser() throws IOException {
        // create jdoe user as a regular user
        createUser(JDOE);
        // re-compute read acls
        transactionalFeature.nextTransaction();

        String entityId = "foo";
        // use rest for creation in order to have the correct author
        var annotation = newExternalAnnotation(file.getId(), "file:content", entityId);
        annotation.setText("Some text");
        String jsonComment = MarshallerHelper.objectToJson(annotation, CtxBuilder.session(session).get());
        httpClient.buildPostRequest("/id/" + file.getId() + "/@annotation")
                  .credentials(JDOE, JDOE)
                  .entity(jsonComment)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // now update the annotation
        annotation.setText("And now I update it");
        jsonComment = MarshallerHelper.objectToJson(annotation, CtxBuilder.session(session).get());
        httpClient.buildPutRequest("/id/" + file.getId() + "/@annotation/external/" + entityId)
                  .credentials(JDOE, JDOE)
                  .entity(jsonComment)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // assert the response
                      assertEquals("And now I update it", node.get(COMMENT_TEXT_FIELD).textValue());
                  });
        transactionalFeature.nextTransaction();
        // assert DB was updated
        var updatedAnnotation = annotationService.getExternalAnnotation(session, file.getId(), entityId);
        assertEquals("And now I update it", updatedAnnotation.getText());
    }

    @Test
    public void testDeleteExternalAnnotation() {
        String xpath = "files:files/0/file";
        String entityId = "foo";
        var annotation = annotationService.createAnnotation(session,
                newExternalAnnotation(file.getId(), xpath, entityId));
        transactionalFeature.nextTransaction();

        assertTrue(session.exists(new IdRef(annotation.getId())));

        httpClient.buildDeleteRequest("/id/" + file.getId() + "/@annotation/external/" + entityId)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
        transactionalFeature.nextTransaction();
        assertFalse(session.exists(new IdRef(annotation.getId())));
    }

    @Test
    public void testGetCommentsOfAnnotations() {
        var annotation1 = annotationService.createAnnotation(session, newAnnotation(file.getId(), "file:content"));
        var annotation2 = annotationService.createAnnotation(session, newAnnotation(file.getId(), "file:content"));
        transactionalFeature.nextTransaction();

        List<String> commentIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Comment comment = newComment(i % 2 == 0 ? annotation1.getId() : annotation2.getId());
            comment = commentManager.createComment(session, comment);
            commentIds.add(comment.getId());

            Comment subComment = newComment(comment.getId());
            subComment = commentManager.createComment(session, subComment);
            commentIds.add(subComment.getId());
        }
        transactionalFeature.nextTransaction();

        Set<String> expectedIds = new HashSet<>(commentIds);
        // GET method is deprecated
        httpClient.buildGetRequest("/id/" + file.getId() + "/@annotation/comments")
                  .addQueryParameter("annotationIds", annotation1.getId())
                  .addQueryParameter("annotationIds", annotation2.getId())
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      Set<String> actualIds = new HashSet<>(node.findValuesAsText("id"));
                      assertEquals(10, actualIds.size());
                      assertEquals(expectedIds, actualIds);
                  });

        // POST method is the right API to use
        String requestBody = "[\"" + String.join("\", \"", annotation1.getId(), annotation2.getId()) + "\"]";
        httpClient.buildPostRequest("/id/" + file.getId() + "/@annotation/comments")
                  .entity(requestBody)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      Set<String> actualIds = new HashSet<>(node.findValuesAsText("id"));
                      assertEquals(10, actualIds.size());
                      assertEquals(expectedIds, actualIds);
                  });
    }

}
