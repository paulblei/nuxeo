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

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.usermanager.NuxeoGroupImpl;
import org.nuxeo.ecm.platform.usermanager.NuxeoPrincipalImpl;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 5.7.3
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(init = RestServerInit.class)
public class UserAndGroupGuardTest extends BaseUserTest {

    protected static final HttpStatusCodeHandler STATUS_CODE_HANDLER = new HttpStatusCodeHandler();

    @Inject
    protected UserManager um;

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> restServerFeature.getRestApiUrl())
                                                                   .credentials("user1", "user1")
                                                                   .contentType(MediaType.APPLICATION_JSON)
                                                                   .build();

    @Test
    public void onlyAdminCanDeleteAUser() {
        // Given a modified user

        // When I call a DELETE on the Rest endpoint
        httpClient.buildDeleteRequest("/user/user2")
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void onlyAdminCanUpdateAUser() throws Exception {
        NuxeoPrincipal user = um.getPrincipal("user1");

        // When i PUT this group
        httpClient.buildPutRequest("/user/" + user.getName())
                  .entity(getPrincipalAsJson(user))
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void onlyAdminCanCreateAUser() throws Exception {
        // Given a new user
        NuxeoPrincipal principal = new NuxeoPrincipalImpl("newuser");

        // When i POST it on the user endpoint
        httpClient.buildPostRequest("/user")
                  .entity(getPrincipalAsJson(principal))
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void onlyAdminCanCreateAGroup() throws Exception {
        // Given a modified group
        NuxeoGroup group = new NuxeoGroupImpl("newGroup");

        // When i POST this group
        httpClient.buildPostRequest("/group/")
                  .entity(getGroupAsJson(group))
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void onlyAdminCanUpdateAGroup() throws Exception {
        // Given a modified group
        NuxeoGroup group = um.getGroup("group1");

        // When i POST this group
        httpClient.buildPutRequest("/group/" + group.getName())
                  .entity(getGroupAsJson(group))
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void onlyAdminCanDeleteGroups() {
        // When i DELETE this group
        httpClient.buildDeleteRequest("/group/group1")
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void onlyAdminCanAddAGroupToAUser() throws Exception {
        // Given a modified group
        NuxeoGroup group = um.getGroup("group1");
        NuxeoPrincipal principal = um.getPrincipal("user1");

        // When i POST this group
        httpClient.buildPostRequest("/group/" + group.getName() + "/user/" + principal.getName())
                  .entity(getGroupAsJson(group))
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void onlyAdminCanRemoveAGroupFromAUser() {
        // Given a modified group
        NuxeoGroup group = um.getGroup("group1");
        NuxeoPrincipal principal = um.getPrincipal("user1");

        // When i DELETE this group
        httpClient.buildDeleteRequest("/group/" + group.getName() + "/user/" + principal.getName())
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void powerUserCantDeleteAdminArtifacts() {
        // Given a power user
        NuxeoPrincipal principal = RestServerInit.getPowerUser();

        // When i try to delete admin user
        httpClient.buildDeleteRequest("/user/Administrator")
                  .credentials(principal.getName(), principal.getName())
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));

        // When i try to delete admin user
        httpClient.buildDeleteRequest("/group/administrators")
                  .credentials(principal.getName(), principal.getName())
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a 403
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));
    }

    @Test
    public void powerUserCanDeleteNonAdminArtifacts() {
        // Given a power user
        NuxeoPrincipal principal = RestServerInit.getPowerUser();

        // When i try to delete admin user
        httpClient.buildDeleteRequest("/user/user2")
                  .credentials(principal.getName(), principal.getName())
                  .executeAndConsume(STATUS_CODE_HANDLER, status -> {
                      // Then it returns a NO_CONTENT response
                      assertEquals(SC_NO_CONTENT, status.intValue());

                      assertNull(um.getPrincipal("user2"));
                  });
    }

}
