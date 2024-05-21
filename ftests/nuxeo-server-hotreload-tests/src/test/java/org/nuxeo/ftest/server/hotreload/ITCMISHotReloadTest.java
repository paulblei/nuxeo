/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
package org.nuxeo.ftest.server.hotreload;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.junit.Assert.assertEquals;
import static org.nuxeo.functionaltests.AbstractTest.NUXEO_URL;

import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;

/**
 * Tests hot reload from CMIS.
 *
 * @since 10.1
 */
public class ITCMISHotReloadTest {

    @Rule
    public final HotReloadTestRule hotReloadRule = new HotReloadTestRule();

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(() -> NUXEO_URL + "/json/cmis");

    @Test
    public void testHotReloadDocumentType() {
        // get root id
        String rootId = httpClient.buildGetRequest("")
                                  .executeAndThen(new JsonNodeHandler(),
                                          node -> node.get("default").get("rootFolderId").asText());

        // test create a document
        Map<String, String> formData = new HashMap<>();
        formData.put("cmisaction", "createDocument");
        formData.put("propertyId[0]", "cmis:objectTypeId");
        formData.put("propertyValue[0]", "HotReload");
        formData.put("propertyId[1]", "cmis:name");
        formData.put("propertyValue[1]", "hot reload");
        formData.put("propertyId[2]", "hr:content");
        formData.put("propertyValue[2]", "some content");
        formData.put("succinct", "true");
        httpClient.buildPostRequest("default/root?objectId=" + rootId)
                  .entity(formData)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
    }

}
