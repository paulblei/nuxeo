/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Contributors:
 *      Kevin Leturc <kleturc@nuxeo.com>
 */

package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.junit.Assert.assertEquals;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_WRITE;

import java.io.IOException;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.schema.PropertyCharacteristicHandler;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tests the secured property handled by {@link PropertyCharacteristicHandler}.
 *
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(RestServerFeature.class)
@Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/test-documentmodel-secured-types-contrib.xml")
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class DocumentSecuredPropertyTest {

    public static final String USER_1 = "user1";

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected CoreSession session;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    @Before
    public void doBefore() {
        ACE ace = ACE.builder(USER_1, READ_WRITE).creator(session.getPrincipal().getName()).isGranted(true).build();
        ACP acp = new ACPImpl();
        acp.addACE(ACL.LOCAL_ACL, ace);
        session.setACP(new PathRef("/folder_2"), acp, false);
        session.save();
        txFeature.nextTransaction();
    }

    @Test
    public void testAdministratorCanCreate() {
        httpClient.buildPostRequest("/path/folder_2")
                  .entity(instantiateDocumentBody())
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
    }

    @Test
    public void testUserCanNotCreate() {
        httpClient.buildPostRequest("/path/folder_2")
                  .credentials(USER_1, USER_1)
                  .entity(instantiateDocumentBody())
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST),
                          node -> assertEquals("Cannot set the value of property: scalar since it is readonly",
                                  JsonNodeHelper.getErrorMessage(node)));
    }

    @Test
    public void testUserCanUseRepositoryUsingEmptyWithDefaultAdapter() throws IOException {
        var root = httpClient.buildGetRequest("/path/folder_2/@emptyWithDefault")
                             .credentials(USER_1, USER_1)
                             .addQueryParameter("type", "Secured")
                             .addQueryParameter("properties", "*")
                             .execute(new JSONDocumentNodeHandler());

        // edit response for next call
        root.node.remove("title");
        root.node.put("name", "file");
        var unsecureComplex = (ObjectNode) root.getPropertyAsJsonNode("secured:unsecureComplex");
        unsecureComplex.put("scalar2", "I can");
        root.setPropertyValue("secured:unsecureComplex", unsecureComplex);

        var statusCodeHandler = new HttpStatusCodeHandler();
        httpClient.buildPostRequest("/path/folder_2")
                  .credentials(USER_1, USER_1)
                  .entity(root.asJson())
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CREATED, status.intValue()));

        // edit response for next call
        root.node.with("properties").removeAll();
        unsecureComplex.put("scalar2", "I still can!");
        root.setPropertyValue("secured:unsecureComplex", unsecureComplex);

        httpClient.buildPostRequest("/path/folder_2")
                  .credentials(USER_1, USER_1)
                  .entity(root.asJson())
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CREATED, status.intValue()));
    }

    @NotNull
    protected String instantiateDocumentBody() {
        return """
                {
                  "entity-type": "document",
                  "name": "secured_document",
                  "type": "Secured",
                  "properties": {
                    "secured:scalar": "I'm secured !"
                  }
                }
                """;
    }

}
