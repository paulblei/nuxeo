/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 */

package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.usermanager.NuxeoGroupImpl;
import org.nuxeo.ecm.platform.usermanager.NuxeoPrincipalImpl;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(RestServerFeature.class)
@RepositoryConfig(init = RestServerInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.restapi.test:test-usermanager-powerusers.xml")
public class UserGroupWithPowerUserTest extends BaseUserTest {

    public static final String ADMINISTRATORS_GROUP = "administrators";

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected UserManager userManager;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> restServerFeature.getRestApiUrl())
                                                                   .credentials("leela", "pwd")
                                                                   .contentType(MediaType.APPLICATION_JSON)
                                                                   .build();

    @Before
    public void before() {
        // power user
        DocumentModel user = userManager.getBareUserModel();
        user.setPropertyValue("user:username", "leela");
        user.setPropertyValue("user:password", "pwd");
        user.setPropertyValue("user:groups", (Serializable) Collections.singletonList("powerusers"));
        userManager.createUser(user);

        // simple user with no group
        user = userManager.getBareUserModel();
        user.setPropertyValue("user:username", "fry");
        userManager.createUser(user);

        NuxeoGroup group = new NuxeoGroupImpl("subgroup");
        group.setParentGroups(Collections.singletonList(ADMINISTRATORS_GROUP));
        userManager.createGroup(group.getModel());

        txFeature.nextTransaction();
    }

    @Test
    public void testPowerUserCannotCreateAdministratorsGroup() throws IOException {
        NuxeoGroup group = new NuxeoGroupImpl("foo");
        String groupJson = getGroupAsJson(group);

        httpClient.buildPostRequest("/group")
                  .entity(groupJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot create artifact", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotUpdateAdministratorsGroup() throws IOException {
        NuxeoGroup group = userManager.getGroup(ADMINISTRATORS_GROUP);
        group.setLabel("foo");
        String groupJson = getGroupAsJson(group);

        httpClient.buildPutRequest("/group/administrators")
                  .entity(groupJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("User is not allowed to edit users", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotDeleteAdministratorsGroup() {
        httpClient.buildDeleteRequest("/group/administrators")
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("User is not allowed to edit users", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotCreateGroupWithParentAdministratorsGroup() throws IOException {
        NuxeoGroup group = new NuxeoGroupImpl("bar");
        group.setParentGroups(Collections.singletonList(ADMINISTRATORS_GROUP));
        String groupJson = getGroupAsJson(group);

        httpClient.buildPostRequest("/group")
                  .entity(groupJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot create artifact", node.get("message").textValue()));

        // subgroup has administrators as parent group
        group = new NuxeoGroupImpl("bar");
        group.setParentGroups(Collections.singletonList("subgroup"));
        groupJson = getGroupAsJson(group);

        httpClient.buildPostRequest("/group")
                  .entity(groupJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot create artifact", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotCreateAdministratorUser() throws IOException {
        NuxeoPrincipal principal = new NuxeoPrincipalImpl("bar");
        principal.setGroups(Collections.singletonList(ADMINISTRATORS_GROUP));
        String userJson = getPrincipalAsJson(principal);

        httpClient.buildPostRequest("/user")
                  .entity(userJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot create artifact", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotUpdateAdministratorUser() throws IOException {
        NuxeoPrincipal user = userManager.getPrincipal("Administrator");
        user.setFirstName("foo");
        String userJson = getPrincipalAsJson(user);

        httpClient.buildPutRequest("/user/Administrator")
                  .entity(userJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("User is not allowed to edit users", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotDeleteAdministratorUser() {
        httpClient.buildDeleteRequest("/user/Administrator")
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("User is not allowed to edit users", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotPromoteUserAsAdministrator() throws IOException {
        NuxeoPrincipal user = userManager.getPrincipal("fry");
        user.setGroups(Collections.singletonList(ADMINISTRATORS_GROUP));
        String userJson = getPrincipalAsJson(user);

        httpClient.buildPutRequest("/user/fry")
                  .entity(userJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("User is not allowed to edit users", node.get("message").textValue()));

        // subgroup has administrators as parent group
        user = userManager.getPrincipal("fry");
        user.setGroups(Collections.singletonList("subgroup"));
        userJson = getPrincipalAsJson(user);

        httpClient.buildPutRequest("/user/fry")
                  .entity(userJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("User is not allowed to edit users", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotPromoteHimselfAsAdministrator() throws IOException {
        NuxeoPrincipal user = userManager.getPrincipal("leela");
        List<String> groups = user.getGroups();
        groups.add(ADMINISTRATORS_GROUP);
        user.setGroups(groups);
        String userJson = getPrincipalAsJson(user);

        httpClient.buildPutRequest("/user/leela")
                  .entity(userJson)
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("User is not allowed to edit users", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotAddAdministratorsGroup() {
        httpClient.buildPostRequest("/user/fry/group/administrators")
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot edit user", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotRemoveAdministratorsGroup() {
        httpClient.buildDeleteRequest("/user/Administrator/group/administrators")
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot edit user", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotAddUserToAdministratorsGroup() {
        httpClient.buildPostRequest("/group/administrators/user/fry")
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot edit user", node.get("message").textValue()));
    }

    @Test
    public void testPowerUserCannotRemoveUserFromAdministratorsGroup() {
        httpClient.buildDeleteRequest("/group/administrators/user/Administrator")
                  .executeAndConsume(new JsonNodeHandler(SC_FORBIDDEN),
                          node -> assertEquals("Cannot edit user", node.get("message").textValue()));
    }
}
