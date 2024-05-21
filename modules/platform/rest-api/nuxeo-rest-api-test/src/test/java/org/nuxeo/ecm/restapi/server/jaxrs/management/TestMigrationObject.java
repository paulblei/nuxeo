/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nour AL KOTOB
 */
package org.nuxeo.ecm.restapi.server.jaxrs.management;

import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 11.3
 */
@Deploy("org.nuxeo.runtime.migration.tests:OSGI-INF/dummy-migration.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-dummy-bulk-migrator.xml")
public class TestMigrationObject extends ManagementBaseTest {

    @Test
    public void testGet() {
        httpClient.buildGetRequest("/management/migration/dummy-migration")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      String json = response.getEntityString();
                      assertJsonResponse(json, "json/testGet.json");
                  }));
    }

    @Test
    public void testGetWrongMigration() {
        httpClient.buildGetRequest("/management/migration/doesNotExistMigration")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    @Test
    public void testGetList() {
        httpClient.buildGetRequest("/management/migration")
                  .executeAndConsume(new JsonNodeHandler(), ThrowableConsumer.asConsumer(node -> {
                      Iterator<JsonNode> elements = node.get("entries").elements();
                      Map<String, String> entries = new HashMap<>();
                      elements.forEachRemaining(n -> entries.put(n.get("id").textValue(), n.toString()));
                      assertJsonResponse(entries.get("dummy-migration"), "json/testGet.json");
                      assertJsonResponse(entries.get("dummy-multi-migration"), "json/testGetMulti.json");
                  }));
    }

    @Test
    public void testProbeMigration() {
        httpClient.buildPostRequest("/management/migration/dummy-migration/probe")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      String json = response.getEntityString();
                      assertJsonResponse(json, "json/testGet.json");
                  }));
    }

    @Test
    public void testRunMigration() {
        // Run a unique available migration step
        httpClient.buildPostRequest("/management/migration/dummy-migration/run")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_ACCEPTED, status.intValue()));
        httpClient.buildGetRequest("/management/migration/dummy-migration")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      String json = response.getEntityString();
                      assertJsonResponse(json, "json/testGetAgain.json");
                  }));
        // Now another migration step is the only one available
        httpClient.buildPostRequest("/management/migration/dummy-migration/run")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_ACCEPTED, status.intValue()));
        httpClient.buildGetRequest("/management/migration/dummy-migration")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      String json = response.getEntityString();
                      assertJsonResponse(json, "json/testGetFinalStep.json");
                  }));
    }

    @Test
    public void testRunFailingMigration() {
        httpClient.buildGetRequest("/management/migration/dummy-failing-bulk-migration")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      String json = response.getEntityString();
                      assertJsonResponse(json, "json/testGetFailing.json");
                  }));
        httpClient.buildPostRequest("/management/migration/dummy-failing-bulk-migration/run")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_ACCEPTED, status.intValue()));
        await().dontCatchUncaughtExceptions().atMost(ONE_MINUTE).untilAsserted(() -> {
            httpClient.buildGetRequest("/management/migration/dummy-failing-bulk-migration")
                      .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                          assertEquals(SC_OK, response.getStatus());
                          String json = response.getEntityString();
                          assertJsonResponse(json, "json/testGetFailed.json");
                      }));
        });
    }

    @Test
    public void testRunMigrationStep() {
        // Can't run without specifying the desired step as there are multiple available steps
        httpClient.buildPostRequest("/management/migration/dummy-multi-migration/run")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_BAD_REQUEST, status.intValue()));
        // Run a specific migration step
        httpClient.buildPostRequest("/management/migration/dummy-multi-migration/run/before-to-reallyAfter")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_ACCEPTED, status.intValue()));
        httpClient.buildGetRequest("/management/migration/dummy-multi-migration")
                  .executeAndConsume(ThrowableConsumer.asConsumer(response -> {
                      assertEquals(SC_OK, response.getStatus());
                      String json = response.getEntityString();
                      assertJsonResponse(json, "json/testGetFinalStepMulti.json");
                  }));
    }

}
