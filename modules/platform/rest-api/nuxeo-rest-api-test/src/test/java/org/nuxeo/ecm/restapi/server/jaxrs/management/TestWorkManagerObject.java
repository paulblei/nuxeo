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
 *     Nour Al Kotob
 */
package org.nuxeo.ecm.restapi.server.jaxrs.management;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 11.3
 */
@RunWith(FeaturesRunner.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test:test-work-dead-letter-queue.xml")
public class TestWorkManagerObject extends ManagementBaseTest {

    @Test
    public void testRunWorksInFailure() {
        httpClient.buildPostRequest("/management/work-manager/run-works-in-failure")
                  .executeAndConsume(new JsonNodeHandler(), result -> {
                      assertEquals(0, result.get("total").asInt());
                      assertEquals(0, result.get("success").asInt());
                  });
    }
}
