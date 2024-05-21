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
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.server.jaxrs.drive;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.transientstore.keyvalueblob.KeyValueBlobTransientStoreFeature;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Tests the {@link NuxeoDriveObject}.
 *
 * @since 9.10
 */
@RunWith(FeaturesRunner.class)
@Features({ KeyValueBlobTransientStoreFeature.class, RestServerFeature.class })
@Deploy("org.nuxeo.drive.rest.api")
public class NuxeoDriveObjectTest {

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void testGetConfiguration() throws URISyntaxException, IOException {
        httpClient.buildGetRequest("/drive/configuration")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));

        File testConfigurationFile = new File(
                Thread.currentThread().getContextClassLoader().getResource("nuxeo-drive-config.json").toURI());
        File serverConfigurationFile = new File(Environment.getDefault().getConfig(), "nuxeo-drive-config.json");
        FileUtils.copyFile(testConfigurationFile, serverConfigurationFile);

        httpClient.buildGetRequest("/drive/configuration").executeAndConsume(new JsonNodeHandler(), options -> {
            assertNotNull(options);
            assertEquals(10, options.size());
            assertEquals(30, options.get("delay").intValue());
        });
    }
}
