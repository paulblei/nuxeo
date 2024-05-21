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
 *     Thomas Roger
 */

package org.nuxeo.ecm.restapi.test;

import static org.junit.Assert.assertEquals;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 9.3
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@Deploy("org.nuxeo.ecm.platform.restapi.test.test")
public class MarshallingEdgeCasesTest {

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule unauthenticatedHttpClient = HttpClientTestRule.builder()
                                                                                  .url(() -> restServerFeature.getRestApiUrl())
                                                                                  .accept(MediaType.APPLICATION_JSON)
                                                                                  .build();

    @Test
    public void unauthenticatedEndpointShouldReturnJSON() {
        unauthenticatedHttpClient.buildGetRequest("/foo/unauthenticated")
                                 .executeAndConsume(new JsonNodeHandler(),
                                         node -> assertEquals("bar", node.get("foo").textValue()));
    }

    @Test
    public void rollbackedTransactionShouldStillReturnJSON() {
        unauthenticatedHttpClient.buildGetRequest("/foo/rollback")
                                 .credentials("Administrator", "Administrator")
                                 .executeAndConsume(new JsonNodeHandler(),
                                         node -> assertEquals("bar", node.get("foo").textValue()));
    }

    // NXP-30854
    @Test
    public void unauthenticatedEndpointShouldReturnDocument() {
        unauthenticatedHttpClient.buildGetRequest("/foo/unauthenticated/doc")
                                 .executeAndConsume(new JsonNodeHandler(), Assert::assertNotNull);
    }

}
