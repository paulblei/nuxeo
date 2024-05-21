/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ecm.webengine.app.JsonWebengineWriter.SHOW_EXCEPTION_MESSAGE;

import java.util.List;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.function.ThrowableRunnable;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.webengine.JsonFactoryManager;
import org.nuxeo.ecm.webengine.app.WebEngineExceptionMapper;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.LogFeature;
import org.nuxeo.runtime.test.runner.LoggerLevel;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

@RunWith(FeaturesRunner.class)
@Features({ LogFeature.class, LogCaptureFeature.class, RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test.test")
public class ExceptionRestTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected LogCaptureFeature.Result logCaptureResult;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected JsonFactoryManager jsonFactoryManager;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void testSimpleException() {
        // Given an existing document
        DocumentModel note = RestServerInit.getNote(0, session);

        // When i do a wrong GET Request
        httpClient.buildGetRequest("/wrongpath" + note.getPathAsString())
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), node -> {
                      // Then i get an exception and parse it to check json payload
                      assertEquals("exception", node.get("entity-type").textValue());
                      assertEquals(SC_NOT_FOUND, node.get("status").intValue());
                      assertEquals("Type not found: wrongpath", node.get("message").textValue());
                  });
    }

    @Test
    public void testExtendedException() {
        JsonFactoryManager jsonFactoryManager = Framework.getService(JsonFactoryManager.class);
        if (!jsonFactoryManager.isStackDisplay()) {
            jsonFactoryManager.toggleStackDisplay();
        }

        // When I do a request with a wrong document ID
        httpClient.buildGetRequest("/path/wrongID").executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), node -> {
            // Then i get an exception and parse it to check json payload
            assertEquals("exception", node.get("entity-type").textValue());
            assertEquals(SC_NOT_FOUND, node.get("status").intValue());
            assertEquals("/wrongID", node.get("message").textValue());
            assertNotNull(node.get("stacktrace").textValue());
            assertEquals(DocumentNotFoundException.class.getCanonicalName(),
                    node.get("exception").get("className").textValue());
        });
    }

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "ERROR", loggerClass = WebEngineExceptionMapper.class)
    public void testNotFoundEndpoint() {
        httpClient.buildGetRequest("/foo/notFound").executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), node -> {
            assertEquals(SC_NOT_FOUND, node.get("status").numberValue());
            assertEquals("com.sun.jersey.api.NotFoundException: null for uri: " + restServerFeature.getRestApiUrl()
                    + "/foo/notFound", node.get("message").textValue());
        });

        List<String> caughtEvents = logCaptureResult.getCaughtEventMessages();
        assertEquals(0, caughtEvents.size());
    }

    // NXP-31672
    @Test
    @WithFrameworkProperty(name = SHOW_EXCEPTION_MESSAGE, value = "false")
    @LogCaptureFeature.FilterOn(logLevel = "ERROR", loggerClass = WebEngineExceptionMapper.class)
    public void testNotFoundEndpointHideExceptionMessage() {
        httpClient.buildGetRequest("/foo/notFound").executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND), node -> {
            assertEquals(SC_NOT_FOUND, node.get("status").numberValue());
            assertEquals("An error occured", node.get("message").textValue());
        });

        List<String> caughtEvents = logCaptureResult.getCaughtEventMessages();
        assertEquals(0, caughtEvents.size());
    }

    @Test
    @LogCaptureFeature.FilterOn(logLevel = "ERROR", loggerClass = WebEngineExceptionMapper.class)
    public void testEndpointWithInternalErrorException() {
        httpClient.buildGetRequest("/foo/exception")
                  .executeAndConsume(new JsonNodeHandler(SC_INTERNAL_SERVER_ERROR), node -> {
                      assertEquals(SC_INTERNAL_SERVER_ERROR, node.get("status").numberValue());
                      assertEquals("foo", node.get("message").textValue());
                  });

        List<String> caughtEvents = logCaptureResult.getCaughtEventMessages();
        assertEquals(1, caughtEvents.size());
        assertEquals("org.nuxeo.ecm.core.api.NuxeoException: foo", caughtEvents.get(0));
    }

    @Test
    @LoggerLevel(klass = WebEngineExceptionMapper.class, level = "DEBUG")
    @LogCaptureFeature.FilterOn(loggerClass = WebEngineExceptionMapper.class, logLevel = "DEBUG")
    public void testEndpointWithBadRequestExceptionNoDevMode() {
        testEndpointWithBadRequestException();
    }

    @Test
    @LogCaptureFeature.FilterOn(loggerClass = WebEngineExceptionMapper.class, logLevel = "WARN")
    @WithFrameworkProperty(name = "org.nuxeo.dev", value = "true")
    public void testEndpointWithBadRequestExceptionInDevMode() {
        testEndpointWithBadRequestException();
    }

    protected void testEndpointWithBadRequestException() {
        httpClient.buildGetRequest("/foo/bad-request").executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> {
            assertEquals(SC_BAD_REQUEST, node.get("status").numberValue());
            assertEquals("bad request", node.get("message").textValue());
        });

        List<String> caughtEvents = logCaptureResult.getCaughtEventMessages();
        assertEquals(1, caughtEvents.size());
        assertEquals("org.nuxeo.ecm.core.api.NuxeoException: bad request", caughtEvents.get(0));
    }

    // NXP-30050
    @Test
    public void testEndpointWithInternalServerErrorException() {
        withNoStackDisplay(() -> testEndpointWithInternalServerErrorException("Internal Server Error"));
    }

    // NXP-30050
    @Test
    @WithFrameworkProperty(name = "org.nuxeo.dev", value = "true")
    public void testEndpointWithInternalServerErrorExceptionInDevMode() {
        withNoStackDisplay(() -> testEndpointWithInternalServerErrorException("a secret message"));
    }

    // NXP-30050
    @Test
    public void testEndpointWithInternalServerErrorExceptionWithStackDisplay() {
        testEndpointWithInternalServerErrorException("a secret message");
    }

    protected void testEndpointWithInternalServerErrorException(String expectedMessage) {
        httpClient.buildGetRequest("/foo/internal-error")
                  .executeAndConsume(new JsonNodeHandler(SC_INTERNAL_SERVER_ERROR), node -> {
                      assertEquals(SC_INTERNAL_SERVER_ERROR, node.get("status").numberValue());
                      assertEquals(expectedMessage, node.get("message").textValue());
                  });
    }

    protected <E extends Throwable> void withNoStackDisplay(ThrowableRunnable<E> runnable) throws E {
        boolean initialStackDisplay = jsonFactoryManager.isStackDisplay();
        try {
            if (initialStackDisplay) {
                jsonFactoryManager.toggleStackDisplay();
            }
            runnable.run();
        } finally {
            if (initialStackDisplay && !jsonFactoryManager.isStackDisplay()) {
                jsonFactoryManager.toggleStackDisplay();
            }
        }
    }
}
