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
 *     dmetzler
 *     Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_PARTIAL_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.trace.Trace;
import org.nuxeo.ecm.automation.core.trace.TracerFactory;
import org.nuxeo.ecm.automation.core.util.PaginableDocumentModelList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.OperationAdapter;
import org.nuxeo.ecm.restapi.server.jaxrs.blob.BlobAdapter;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 5.7.2 - Test the Rest binding to run operations
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@Deploy("org.nuxeo.ecm.platform.restapi.test:operation-contrib.xml")
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class OperationBindingTest {

    private static String PARAMS = "{\"params\":{\"one\":\"1\",\"two\": 2}}";

    @Inject
    protected CoreSession session;

    @Inject
    protected TracerFactory factory;

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    @Before
    public void doBefore() {
        // Activate trace mode
        if (!factory.getRecordingState()) {
            factory.toggleRecording();
        }
    }

    @Test
    public void itCanRunAnOperationOnADocument() {
        // Given a document and an operation
        DocumentModel note = RestServerInit.getNote(0, session);

        // When I call the REST binding on the document resource
        httpClient.buildPostRequest("/id/" + note.getId() + "/@" + OperationAdapter.NAME + "/testOp")
                  .entity(PARAMS)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Then the operation call succeeds
                          status -> assertEquals(SC_OK, status.intValue()));

        // Then the Automation trace contains one call
        Trace trace = factory.getTrace("testOp");
        assertEquals(1, trace.getCalls().size());

        // Then the call variables contains the chain parameters
        Map<String, Object> variables = trace.getCalls().get(0).getVariables();
        @SuppressWarnings("unchecked")
        Map<String, Object> chainParameters = (Map<String, Object>) variables.get(Constants.VAR_RUNTIME_CHAIN);
        assertNotNull(chainParameters);
        assertEquals("1", chainParameters.get("one"));
        assertEquals(2, chainParameters.get("two"));

        // Then the Automation trace output is the document
        assertEquals(note.getId(), ((DocumentModel) trace.getOutput()).getId());
    }

    @Test
    public void itCanRunAChainOnADocument() {
        // Given a document and an operation
        DocumentModel note = RestServerInit.getNote(0, session);

        // When I call the REST binding on the document resource
        httpClient.buildPostRequest("/id/" + note.getId() + "/@" + OperationAdapter.NAME + "/testChain")
                  .entity("{}")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Then the operation call succeeds
                          status -> assertEquals(SC_OK, status.intValue()));

        // Then the Automation trace contains two calls
        Trace trace = factory.getTrace("testChain");
        assertEquals(2, trace.getCalls().size());

        // Then the call parameters contains the operation parameters
        Map<String, Object> parameters = trace.getCalls().get(0).getParameters();
        assertEquals("One", parameters.get("one"));
        assertEquals(2L, parameters.get("two"));

        parameters = trace.getCalls().get(1).getParameters();
        assertEquals(4L, parameters.get("two"));
        assertEquals("Two", parameters.get("one"));

        // Then the Automation trace output is the document
        assertEquals(note.getId(), ((DocumentModel) trace.getOutput()).getId());
    }

    @Test
    public void itCanRunAChainOnMutlipleDocuments() {
        // Given a folder
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When I call the REST binding on the children resource
        httpClient.buildPostRequest("/id/" + folder.getId() + "/@children/@" + OperationAdapter.NAME + "/testOp")
                  .entity(PARAMS)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Then the operation call succeeds
                          status -> assertEquals(SC_OK, status.intValue()));

        // Then the Automation trace contains one call
        Trace trace = factory.getTrace("testOp");
        assertEquals(1, trace.getCalls().size());

        // Then the Automation trace output is the list of children documents
        assertEquals(session.getChildren(folder.getRef()).size(),
                ((PaginableDocumentModelList) trace.getOutput()).size());
    }

    @Test
    public void itCanRunAutomationWithBlob() {
        // Given a file
        DocumentModel file = RestServerInit.getFile(1, session);

        // When i call the REST binding on the blob resource
        httpClient.buildPostRequest(
                "/id/" + file.getId() + "/@" + BlobAdapter.NAME + "/file:content/@" + OperationAdapter.NAME + "/testOp")
                  .entity(PARAMS)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Then the operation call succeeds
                          status -> assertEquals(SC_OK, status.intValue()));

        // Then the Automation trace contains one call
        Trace trace = factory.getTrace("testOp");

        // Then the Automation trace output is a blob
        assertTrue(trace.getOutput() instanceof Blob);
    }

    @Test
    public void automationResourceIsAlsoAvailableBehindAPIRoot() {
        httpClient.buildGetRequest("/automation/doc")
                  .accept("text/html")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    /**
     * @since 7.1
     */
    @Test
    public void itShouldReturnCustomHttpStatusWhenSuccess() {
        String param = "{\"params\":{\"isFailing\":\"false\"}}";
        httpClient.buildPostRequest("/@" + OperationAdapter.NAME + "/Test.HttpStatus")
                  .entity(param)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_PARTIAL_CONTENT, status.intValue()));
    }

    /**
     * @since 7.1
     */
    @Test
    public void itShouldReturnCustomHttpStatusWhenFailure() {
        String param = "{\"params\":{\"isFailing\":\"true\"}}";
        httpClient.buildPostRequest("/@" + OperationAdapter.NAME + "/Test.HttpStatus")
                  .entity(param)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_METHOD_NOT_ALLOWED, status.intValue()));
    }
}
