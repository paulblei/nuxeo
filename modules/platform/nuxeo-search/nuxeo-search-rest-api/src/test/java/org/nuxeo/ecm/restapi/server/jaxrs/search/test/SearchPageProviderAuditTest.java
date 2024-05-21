/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.server.jaxrs.search.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.audit.provider.LatestCreatedUsersOrGroupsPageProvider.LATEST_CREATED_USERS_OR_GROUPS_PROVIDER;

import java.util.List;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.restapi.test.JsonNodeHelper;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 2021.12
 */
@RunWith(FeaturesRunner.class)
@Features({ AuditFeature.class, SearchRestFeature.class })
public class SearchPageProviderAuditTest {

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected UserManager userManager;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void iCanPerformPageProviderOnAudit() {
        // Request the PageProvider while there's nothing to return
        httpClient.buildGetRequest("/search/pp/" + LATEST_CREATED_USERS_OR_GROUPS_PROVIDER + "/execute")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertTrue(JsonNodeHelper.getEntries(node).isEmpty()));

        // Then create some data
        DocumentModel groupModel = userManager.getBareGroupModel();
        groupModel.setProperty("group", "groupname", "my_group");
        groupModel.setProperty("group", "grouplabel", "My Group");
        groupModel.setProperty("group", "description", "description of my_group");
        userManager.createGroup(groupModel);

        DocumentModel userModel = userManager.getBareUserModel();
        userModel.setProperty("user", "username", "my_user");
        userModel.setProperty("user", "firstName", "My");
        userModel.setProperty("user", "lastName", "User");
        userModel.setProperty("user", "password", "my_user");
        userManager.createUser(userModel);

        txFeature.nextTransaction();

        // Then request the data
        httpClient.buildGetRequest("/search/pp/" + LATEST_CREATED_USERS_OR_GROUPS_PROVIDER + "/execute")
                  .addHeader("properties", "*")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                      assertEquals(2, entries.size());
                      JsonNode jsonNode = entries.get(0);
                      assertEquals("my_user", jsonNode.get("title").asText());
                      assertEquals("My", jsonNode.get("properties").get("user:firstName").asText());
                      assertEquals("User", jsonNode.get("properties").get("user:lastName").asText());
                      jsonNode = entries.get(1);
                      assertEquals("my_group", jsonNode.get("title").asText());
                      assertEquals("My Group", jsonNode.get("properties").get("group:grouplabel").asText());
                      assertEquals("description of my_group",
                              jsonNode.get("properties").get("group:description").asText());
                  });
    }
}
