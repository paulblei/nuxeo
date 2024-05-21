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

import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.StringHandler;
import org.nuxeo.http.test.handler.VoidHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 5.8
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test:multiblob-doctype.xml")
public class MultiBlobAccessTest {

    @Inject
    CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    private DocumentModel doc;

    @Before
    public void doBefore() {
        doc = session.createDocumentModel("/", "testBlob", "MultiBlobDoc");
        addBlob(doc, Blobs.createBlob("one"));
        addBlob(doc, Blobs.createBlob("two"));
        doc = session.createDocument(doc);
        transactionalFeature.nextTransaction();
    }

    @Test
    public void itDoesNotUpdateBlobsThroughDocEndpoint() throws Exception {
        String docJsonIN = httpClient.buildGetRequest("/path" + doc.getPathAsString())
                                     .addHeader("properties", "multiblob")
                                     .execute(new StringHandler());
        httpClient.buildPutRequest("/path" + doc.getPathAsString())
                  .addHeader("properties", "multiblob")
                  .entity(docJsonIN)
                  .execute(new VoidHandler());
        DocumentModel doc = session.getDocument(new PathRef("/testBlob"));
        assertEquals(2, ((List<?>) doc.getProperty("mb:blobs").getValue()).size());
        Blob blob1 = (Blob) doc.getProperty("mb:blobs/0/content").getValue();
        assertNotNull(blob1);
        assertEquals("one", blob1.getString());
        Blob blob2 = (Blob) doc.getProperty("mb:blobs/1/content").getValue();
        assertNotNull(blob2);
        assertEquals("two", blob2.getString());
    }

    @Test
    public void itCanAccessBlobs() {
        // When i call the rest api
        httpClient.buildGetRequest("/path" + doc.getPathAsString() + "/@blob/mb:blobs/0/content")
                  .executeAndConsume(new StringHandler(),
                          // Then i receive the content of the blob
                          body -> assertEquals("one", body));

        httpClient.buildGetRequest("/path" + doc.getPathAsString() + "/@blob/mb:blobs/1/content")
                  .executeAndConsume(new StringHandler(),
                          // Then i receive the content of the blob
                          body -> assertEquals("two", body));
    }

    @Test
    public void itCanModifyABlob() throws Exception {
        // Given a doc with a blob

        // When i send a PUT with a new value on the blob
        var entity = MultipartEntityBuilder.create()
                                           .addBinaryBody("content", "modifiedData".getBytes(), ContentType.TEXT_PLAIN,
                                                   "content.txt")
                                           .build();
        try (InputStream requestBody = entity.getContent()) {
            httpClient.buildPutRequest("path" + doc.getPathAsString() + "/@blob/mb:blobs/0/content")
                      .addHeader("Content-Type", entity.getContentType().getValue())
                      .entity(requestBody)
                      .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
        }
        // The blob is updated
        transactionalFeature.nextTransaction();
        doc = getTestBlob();
        Blob blob = (Blob) doc.getPropertyValue("mb:blobs/0/content");
        assertEquals("modifiedData", blob.getString());
    }

    @Test
    public void itCanRemoveABlob() {
        // Given a doc with a blob

        // When i send A DELETE command on its blob
        httpClient.buildDeleteRequest("/path" + doc.getPathAsString() + "/@blob/mb:blobs/0/content")
                  .execute(new VoidHandler());

        // The the blob is reset
        transactionalFeature.nextTransaction();
        doc = getTestBlob();
        Blob blob = (Blob) doc.getPropertyValue("mb:blobs/0/content");
        assertNull(blob);
    }

    private DocumentModel getTestBlob() {
        return session.getDocument(new PathRef("/testBlob"));
    }

    private void addBlob(DocumentModel doc, Blob blob) {
        Map<String, Serializable> blobProp = new HashMap<>();
        blobProp.put("content", (Serializable) blob);
        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> blobs = (List<Map<String, Serializable>>) doc.getPropertyValue("mb:blobs");
        blobs.add(blobProp);
        doc.setPropertyValue("mb:blobs", (Serializable) blobs);
    }

    @Test
    public void itCanAccessBlobsThroughBlobHolder() {
        DocumentModel doc = getTestBlob();
        BlobHolder bh = doc.getAdapter(BlobHolder.class);
        bh.setBlob(Blobs.createBlob("main"));
        doc = session.saveDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // When i call the rest api
        httpClient.buildGetRequest("/path" + doc.getPathAsString() + "/@blob/blobholder:0")
                  .executeAndConsume(new StringHandler(),
                          // Then i receive the content of the blob
                          body -> assertEquals("main", body));

        httpClient.buildGetRequest("/path" + doc.getPathAsString() + "/@blob/blobholder:1")
                  .executeAndConsume(new StringHandler(),
                          // Then i receive the content of the blob
                          body -> assertEquals("one", body));

        httpClient.buildGetRequest("/path" + doc.getPathAsString() + "/@blob/blobholder:2")
                  .executeAndConsume(new StringHandler(),
                          // Then i receive the content of the blob
                          body -> assertEquals("two", body));
    }

}
