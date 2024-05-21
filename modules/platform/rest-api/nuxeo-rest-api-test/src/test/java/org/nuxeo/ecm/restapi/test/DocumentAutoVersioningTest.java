/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
 *
 */

package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.api.security.ACL.LOCAL_ACL;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.restapi.jaxrs.io.RestConstants;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.http.test.handler.VoidHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 9.1
 */
@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class, RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class DocumentAutoVersioningTest {

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
    @Deploy("org.nuxeo.ecm.platform.restapi.test.test:source-based-versioning-contrib.xml")
    public void iCanUpdateDocumentWithSourceCondition() throws Exception {
        DocumentModel note = RestServerInit.getNote(0, session);
        assertEquals("0.1", note.getVersionLabel());

        JSONDocumentNode jsonDoc = httpClient.buildGetRequest("/id/" + note.getId())
                                             .addHeader("X-NXDocumentProperties", "dublincore")
                                             .execute(new JSONDocumentNodeHandler());

        jsonDoc.setPropertyValue("dc:title", "New title !");
        httpClient.buildPutRequest("/id/" + note.getId())
                  .addHeader(RestConstants.SOURCE, "REST")
                  .entity(jsonDoc.asJson())
                  .execute(new VoidHandler());
        transactionalFeature.nextTransaction();

        note = RestServerInit.getNote(0, session);
        assertEquals("1.0", note.getVersionLabel());
    }

    @Test
    public void iCanDoCollaborativeVersioning() {
        // This test should check the default behaviour which is collaborative versioning
        // meaning the minor version should increment if the last contributor has changed
        DocumentModel folder = RestServerInit.getFolder(0, session);

        String data = "{ " + "     \"entity-type\": \"document\"," + "     \"type\": \"File\","
                + "     \"name\":\"myFile\"," + "     \"properties\": {" + "         \"dc:title\":\"My title\""
                + "     }" + "}";
        String id = httpClient.buildPostRequest("/path" + folder.getPathAsString())
                              .entity(data)
                              .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> node.get("uid").asText());
        assertTrue(StringUtils.isNotBlank(id));

        transactionalFeature.nextTransaction();
        // Add 'Everything' permission for user0
        ACPImpl acp = new ACPImpl();
        ACLImpl acl = new ACLImpl(LOCAL_ACL);
        ACE ace = new ACE("user0", EVERYTHING, true);
        acl.add(ace);
        acp.addACL(acl);

        DocumentRef idRef = new IdRef(id);
        session.setACP(idRef, acp, true);
        transactionalFeature.nextTransaction();

        DocumentModel doc = session.getDocument(idRef);
        assertEquals("Administrator", doc.getPropertyValue("dc:lastContributor"));
        assertEquals("0.0", doc.getVersionLabel());

        String payload = "{  " + "         \"entity-type\": \"document\"," + "         \"name\": \"myFile\","
                + "         \"type\": \"File\"," + "         \"state\": \"project\","
                + "         \"title\": \"New title\"," + "         \"properties\": {"
                + "             \"dc:description\":\"myDesc\"" + "         }" + "     }";
        httpClient.buildPutRequest("/id/" + doc.getId())
                  .credentials("user0", "user0")
                  .entity(payload)
                  .execute(new VoidHandler());

        transactionalFeature.nextTransaction();
        DocumentModel lastVersion = session.getLastDocumentVersion(idRef);
        assertFalse(lastVersion.isCheckedOut());
        assertEquals("0.1", lastVersion.getVersionLabel());
        doc = session.getDocument(idRef);
        assertTrue(doc.isCheckedOut());
        assertEquals("user0", doc.getPropertyValue("dc:lastContributor"));
        assertEquals("0.1+", doc.getVersionLabel());
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.test.test:time-based-versioning-contrib.xml")
    public void iCanDoTimeBasedVersioning() throws InterruptedException {
        DocumentModel folder = RestServerInit.getFolder(0, session);

        String data = "{ " + "     \"entity-type\": \"document\"," + "     \"type\": \"File\","
                + "     \"name\":\"myFile\"," + "     \"properties\": {" + "         \"dc:title\":\"My title\""
                + "     }" + "}";

        String id = httpClient.buildPostRequest("/path" + folder.getPathAsString())
                              .entity(data)
                              .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> node.get("uid").asText());
        assertTrue(StringUtils.isNotBlank(id));

        Thread.sleep(1000);
        transactionalFeature.nextTransaction();

        String payload = "{  " + "         \"entity-type\": \"document\"," + "         \"name\": \"myFile\","
                + "         \"type\": \"File\"," + "         \"state\": \"project\","
                + "         \"title\": \"New title\"," + "         \"properties\": {"
                + "             \"dc:description\":\"myDesc\"" + "         }" + "     }";
        httpClient.buildPutRequest("/id/" + id).entity(payload).execute(new VoidHandler());
        transactionalFeature.nextTransaction();

        DocumentModel doc = session.getDocument(new IdRef(id));
        assertEquals("1.0", doc.getVersionLabel());
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.test.test:lifecycle-based-versioning-contrib.xml")
    public void iCanDoLifeCycleBasedVersioning() {
        DocumentModel folder = RestServerInit.getFolder(0, session);

        String data = "{ " + "     \"entity-type\": \"document\"," + "     \"type\": \"File\","
                + "     \"name\":\"myFile\"," + "     \"properties\": {" + "         \"dc:title\":\"My title\""
                + "     }" + "}";
        String id = httpClient.buildPostRequest("/path" + folder.getPathAsString())
                              .entity(data)
                              .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> node.get("uid").asText());
        assertTrue(StringUtils.isNotBlank(id));

        transactionalFeature.nextTransaction();
        DocumentRef idRef = new IdRef(id);
        DocumentModel doc = session.getDocument(idRef);
        doc.followTransition("approve");
        session.saveDocument(doc);

        transactionalFeature.nextTransaction();
        doc = session.getDocument(idRef);
        assertEquals("0.1", doc.getVersionLabel());
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.test.test:creation-versioning-contrib.xml")
    public void iCanCreateDocumentWithVersioningPolicy() {
        DocumentModel folder1 = RestServerInit.getFolder(1, session);
        DocumentModel folder2 = RestServerInit.getFolder(2, session);

        String data =
                "{ " + "     \"entity-type\": \"document\"," + "     \"type\": \"File\"," + "     \"name\":\"myFile\","
                        + "     \"properties\": {" + "         \"dc:title\":\"My title\"" + "     }" + "}";
        DocumentRef idRef = httpClient.buildPostRequest("/path" + folder1.getPathAsString())
                                      .addHeader(RestConstants.X_VERSIONING_OPTION, "MINOR")
                                      .entity(data)
                                      .executeAndThen(new JsonNodeHandler(SC_CREATED),
                                              node -> new IdRef(node.get("uid").asText()));
        transactionalFeature.nextTransaction();
        DocumentModel doc = session.getDocument(idRef);
        assertEquals("0.1", doc.getVersionLabel());

        idRef = httpClient.buildPostRequest("/path" + folder2.getPathAsString())
                          .addHeader(RestConstants.X_VERSIONING_OPTION, "MAJOR")
                          .entity(data)
                          .executeAndThen(new JsonNodeHandler(SC_CREATED), node -> new IdRef(node.get("uid").asText()));
        transactionalFeature.nextTransaction();
        doc = session.getDocument(idRef);
        assertEquals("1.0", doc.getVersionLabel());
    }
}
