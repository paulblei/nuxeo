/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nelson Silva <nsilva@nuxeo.com>
 */

package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.http.test.handler.StringHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 8.3
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@Deploy("org.nuxeo.ecm.platform.login.token")
@Deploy("org.nuxeo.ecm.platform.restapi.server.login.tokenauth")
public class AuthenticationTokensTest {

    @Inject
    TokenAuthenticationService tokenAuthenticationService;

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void itCanQueryTokens() {
        // Check empty token list
        httpClient.buildGetRequest("/token")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertTrue(JsonNodeHelper.getEntries("tokens", node).isEmpty()));

        // acquire some tokens
        String token1 = tokenAuthenticationService.acquireToken("Administrator", "app1", "device1", "", "rw");
        coreFeature.getStorageConfiguration().maybeSleepToNextSecond();
        String token2 = tokenAuthenticationService.acquireToken("Administrator", "app2", "device2", "", "rw");

        transactionalFeature.nextTransaction();

        // query tokens for current user
        List<JsonNode> tokens = getTokens();
        assertEquals(2, tokens.size());
        assertEquals(token2, tokens.get(0).get("id").textValue());
        assertEquals(token1, tokens.get(1).get("id").textValue());

        // filter tokens by application
        tokens = getTokens("app1");
        assertEquals(1, tokens.size());
        assertEquals(token1, tokens.get(0).get("id").textValue());
    }

    @Test
    public void itCanRevokeTokens() {
        // acquire a token
        String token1 = tokenAuthenticationService.acquireToken("Administrator", "app1", "device1", "", "rw");
        transactionalFeature.nextTransaction();

        // delete it
        httpClient.buildDeleteRequest("/token/" + token1)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        // check no tokens
        List<JsonNode> tokens = getTokens();
        assertEquals(0, tokens.size());
    }

    @Test
    public void itCanCreateTokens() {
        // acquire a token
        httpClient.buildPostRequest("/token")
                  .addQueryParameter("application", "app")
                  .addQueryParameter("deviceId", "device")
                  .addQueryParameter("permission", "rw")
                  .executeAndConsume(new StringHandler(SC_CREATED), tokenId -> assertFalse(tokenId.isEmpty()));

        // check tokens for current user
        List<JsonNode> tokens = getTokens();
        assertEquals(1, tokens.size());
        JsonNode token = tokens.get(0);
        assertEquals("app", token.get("application").textValue());
        assertEquals("device", token.get("deviceId").textValue());
        assertEquals("rw", token.get("permission").textValue());
        assertFalse(token.get("creationDate").textValue().isEmpty());
        assertFalse(token.get("username").textValue().isEmpty());
    }

    private List<JsonNode> getTokens() {
        return getTokens(null);
    }

    private List<JsonNode> getTokens(String application) {
        var requestBuilder = httpClient.buildGetRequest("/token");
        if (application != null) {
            requestBuilder.addQueryParameter("application", application);
        }
        return requestBuilder.executeAndThen(new JsonNodeHandler(), node -> JsonNodeHelper.getEntries("tokens", node));
    }
}
