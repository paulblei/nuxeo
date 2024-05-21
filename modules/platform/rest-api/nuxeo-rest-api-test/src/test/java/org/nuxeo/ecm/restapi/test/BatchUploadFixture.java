/*
 * (C) Copyright 2015-2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     ataillefer
 *     Gabriel Barata
 *     Mickaël Schoentgen
 */
package org.nuxeo.ecm.restapi.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NOT_IMPLEMENTED;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.automation.server.jaxrs.batch.BatchManagerComponent.DEFAULT_BATCH_HANDLER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.core.operations.blob.AttachBlob;
import org.nuxeo.ecm.automation.core.operations.blob.CreateBlob;
import org.nuxeo.ecm.automation.server.jaxrs.batch.BatchManager;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.io.NginxConstants;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.http.test.handler.VoidHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.transientstore.test.TransientStoreFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @since 7.10
 */
@RunWith(FeaturesRunner.class)
@Features({ TransientStoreFeature.class, RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test:multiblob-doctype.xml")
@Deploy("org.nuxeo.ecm.platform.restapi.test:test-conflict-batch-handler.xml")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-validation-activation-contrib.xml")
public class BatchUploadFixture {

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected CoreSession session;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    /**
     * Tests the /upload endpoints.
     *
     * @since 7.4
     */
    @Test
    public void itCanUseBatchUpload() throws IOException {
        itCanUseBatchUpload(false);
    }

    /**
     * Tests the /upload endpoints with the "X-Batch-No-Drop" header set to true.
     *
     * @since 8.4
     */
    @Test
    public void itCanUseBatchUploadNoDrop() throws IOException {
        itCanUseBatchUpload(true);
    }

    private void itCanUseBatchUpload(boolean noDrop) throws IOException {

        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload a file
        String fileName1 = URLEncoder.encode("Fichier accentué 1.txt", UTF_8);
        String mimeType = "text/plain";
        String data1 = "Contenu accentué du premier fichier";
        String fileSize1 = String.valueOf(getUTF8Bytes(data1).length);

        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName1)
                  .addHeader("X-File-Size", fileSize1)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data1)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                      // TODO NXP-18247 when the actual uploaded size is returned
                      // assertEquals(fileSize1, node.get("uploadedSize").asText());
                  });

        // Upload another file
        String fileName2 = "Fichier accentué 2.txt";
        String data2 = "Contenu accentué du deuxième fichier";
        String fileSize2 = String.valueOf(getUTF8Bytes(data2).length);

        httpClient.buildPostRequest("/upload/" + batchId + "/1")
                  .addHeader("X-File-Name", fileName2)
                  .addHeader("X-File-Size", fileSize2)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data2)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("1", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                  });

        // Get batch info
        httpClient.buildGetRequest("/upload/" + batchId).executeAndConsume(new JsonNodeHandler(), nodes -> {
            assertEquals(2, nodes.size());
            JsonNode node = nodes.get(0);
            assertEquals("Fichier accentué 1.txt", node.get("name").asText());
            assertEquals(fileSize1, node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
            node = nodes.get(1);
            assertEquals("Fichier accentué 2.txt", node.get("name").asText());
            assertEquals(fileSize2, node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
        });

        // Get file infos
        httpClient.buildGetRequest("/upload/" + batchId + "/0").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("Fichier accentué 1.txt", node.get("name").asText());
            assertEquals(fileSize1, node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
        });

        httpClient.buildGetRequest("/upload/" + batchId + "/1").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("Fichier accentué 2.txt", node.get("name").asText());
            assertEquals(fileSize2, node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
        });

        // Create a doc which references the uploaded blobs using the Document path endpoint
        String json = """
                {
                  "entity-type": "document",
                  "name": "testBatchUploadDoc",
                  "type": "MultiBlobDoc",
                  "properties": {
                    "mb:blobs": [
                      { "content": { "upload-batch": "%s", "upload-fileId": "0" } },
                      { "content": { "upload-batch": "%s", "upload-fileId": "1" } }
                    ]
                  }
                }
                """.formatted(batchId, batchId);
        var requestBuilder = httpClient.buildPostRequest("/path/").entity(json).contentType(MediaType.APPLICATION_JSON);
        if (noDrop) {
            requestBuilder.addHeader("X-Batch-No-Drop", "true");
        }
        requestBuilder.executeAndConsume(new HttpStatusCodeHandler(),
                status -> assertEquals(SC_CREATED, status.intValue()));

        txFeature.nextTransaction();

        DocumentModel doc = session.getDocument(new PathRef("/testBatchUploadDoc"));
        Blob blob = (Blob) doc.getPropertyValue("mb:blobs/0/content");
        assertNotNull(blob);
        assertEquals("Fichier accentué 1.txt", blob.getFilename());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(data1, blob.getString());
        blob = (Blob) doc.getPropertyValue("mb:blobs/1/content");
        assertNotNull(blob);
        assertEquals("Fichier accentué 2.txt", blob.getFilename());
        assertEquals(mimeType, blob.getMimeType());
        assertEquals(data2, blob.getString());

        if (noDrop) {
            assertBatchExists(batchId);
        }
    }

    @Test
    public void testBatchExecuteWithUnknownFileIdx() {
        // Get a batchId
        String batchId = initializeNewBatch();

        // Omit to upload a file, the fileIdx "0" will be inexistent then;
        // and ensure to hit a HTTP 404 error and not HTTP 500 as it was before NXP-30348.
        String json = """
                {
                  "params": {
                    "document": "some document"
                  }
                }
                """;
        httpClient.buildPostRequest("/upload/" + batchId + "/0/execute/Blob.Attach")
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    /**
     * tests if the X-File-Type header is obeyed on multipart file upload (NXP-22408)
     */
    @Test
    public void testObeyFileTypeHeader() throws IOException {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload a file without the X-File-Type header
        String data1 = "File without explicit file type";
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("X-File-Name", "No header.txt")
                  .entity(data1)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                  });

        // Upload a file with the X-File-Type header
        String data2 = "File with explicit X-File-Type header";
        String mimeType = "text/plain";
        httpClient.buildPostRequest("/upload/" + batchId + "/1")
                  .addHeader("X-File-Type", mimeType)
                  .addHeader("X-File-Name", "With header.txt")
                  .entity(data2)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("1", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                  });

        // Create a doc which references the uploaded blobs using the Document path endpoint
        String json = "{";
        json += "\"entity-type\":\"document\" ,";
        json += "\"name\":\"testBatchUploadDoc\" ,";
        json += "\"type\":\"MultiBlobDoc\" ,";
        json += "\"properties\" : {";
        json += "\"mb:blobs\" : [ ";
        json += "{ \"content\" : { \"upload-batch\": \"" + batchId + "\", \"upload-fileId\": \"0\" } },";
        json += "{ \"content\" : { \"upload-batch\": \"" + batchId + "\", \"upload-fileId\": \"1\" } }";
        json += "]}}";

        httpClient.buildPostRequest("/path/")
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        txFeature.nextTransaction();

        // verify the created document
        DocumentModel doc = session.getDocument(new PathRef("/testBatchUploadDoc"));
        Blob blob = (Blob) doc.getPropertyValue("mb:blobs/0/content");
        assertNotNull(blob);
        assertEquals("No header.txt", blob.getFilename());
        // No mime type was set
        assertNull(blob.getMimeType());
        assertEquals(data1, blob.getString());
        blob = (Blob) doc.getPropertyValue("mb:blobs/1/content");
        assertNotNull(blob);
        assertEquals("With header.txt", blob.getFilename());
        // X-File-Type header mime type must be set
        assertEquals(mimeType, blob.getMimeType());
        assertEquals(data2, blob.getString());
    }

    /**
     * Tests the use of /upload + /upload/{batchId}/{fileIdx}/execute.
     *
     * @since 7.4
     */
    @Test
    public void testBatchExecute() throws IOException {
        testBatchExecute(false);
    }

    /**
     * Tests the use of /upload + /upload/{batchId}/{fileIdx}/execute with the "X-Batch-No-Drop" header set to true.
     *
     * @since 8.4
     */
    @Test
    public void testBatchExecuteNoDrop() throws IOException {
        testBatchExecute(true);
    }

    private void testBatchExecute(boolean noDrop) throws IOException {

        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload file
        String fileName = URLEncoder.encode("Fichier accentué.txt", UTF_8);
        String mimeType = "text/plain";
        String data = "Contenu accentué";
        String fileSize = String.valueOf(getUTF8Bytes(data).length);
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("X-File-Size", fileSize)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data)
                  .execute(new VoidHandler());

        // Create a doc and attach the uploaded blob to it using the /upload/{batchId}/{fileIdx}/execute endpoint
        DocumentModel file = session.createDocumentModel("/", "testBatchExecuteDoc", "File");
        file = session.createDocument(file);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        String json = """
                {
                  "params": {
                    "document": "%s"
                  }
                }
                """.formatted(file.getPathAsString());
        var requestBuilder = httpClient.buildPostRequest("/upload/" + batchId + "/0/execute/Blob.Attach")
                                       .entity(json)
                                       .contentType(MediaType.APPLICATION_JSON);
        if (noDrop) {
            requestBuilder.addHeader("X-Batch-No-Drop", "true");
        }
        requestBuilder.executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        DocumentModel doc = session.getDocument(new PathRef("/testBatchExecuteDoc"));
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(blob);
        assertEquals("Fichier accentué.txt", blob.getFilename());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(data, blob.getString());

        if (noDrop) {
            assertBatchExists(batchId);
        }
    }

    /**
     * @since 8.1O
     */
    @Test
    public void testBatchExecuteAutomationServerBindings() throws IOException {

        String batchId = initializeNewBatch();

        File file = Framework.createTempFile("nx-test-blob-", ".tmp");
        try {
            CreateBlob.skipProtocolCheck = true;
            String json = """
                    {
                      "params": {
                        "file": "%s"
                      }
                    }
                    """.formatted(file.toURI().toURL());
            var statusCodeHandler = new HttpStatusCodeHandler();
            httpClient.buildPostRequest("/upload/" + batchId + "/execute/Blob.CreateFromURL")
                      .credentials("user1", "user1")
                      .entity(json)
                      .contentType(MediaType.APPLICATION_JSON)
                      .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_FORBIDDEN, status.intValue()));

            // Batch has been cleaned up by the previous call
            httpClient.buildPostRequest("/upload/" + batchId + "/execute/Blob.CreateFromURL")
                      .entity(json)
                      .contentType(MediaType.APPLICATION_JSON)
                      .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NOT_FOUND, status.intValue()));
            // Create a new batch
            batchId = initializeNewBatch();
            httpClient.buildPostRequest("/upload/" + batchId + "/execute/Blob.CreateFromURL")
                      .entity(json)
                      .contentType(MediaType.APPLICATION_JSON)
                      .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_OK, status.intValue()));
        } finally {
            CreateBlob.skipProtocolCheck = false;
            file.delete();
        }
    }

    // NXP-31721
    @Test
    public void testBatchExecuteAttachBlobs() {

        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload a file
        String fileName1 = "Fichier accentué 1.txt";
        String mimeType = "text/plain";
        String data1 = "Contenu accentué du premier fichier";
        String fileSize1 = String.valueOf(getUTF8Bytes(data1).length);

        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName1)
                  .addHeader("X-File-Size", fileSize1)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data1)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                      // TODO NXP-18247 when the actual uploaded size is returned
                      // assertEquals(fileSize1, node.get("uploadedSize").asText());
                  });

        // Upload another file
        String fileName2 = "Fichier accentué 2.txt";
        String data2 = "Contenu accentué du deuxième fichier";
        String fileSize2 = String.valueOf(getUTF8Bytes(data2).length);

        httpClient.buildPostRequest("/upload/" + batchId + "/1")
                  .addHeader("X-File-Name", fileName2)
                  .addHeader("X-File-Size", fileSize2)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data2)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("1", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                  });

        // create a document to receive the batch
        var doc = session.createDocumentModel("/", "testBatchExecuteDoc", "File");
        doc = session.createDocument(doc);
        txFeature.nextTransaction();

        // attach blobs in the batch to the document
        String json = """
                {
                  "params": {
                    "document": {
                      "entity-type": "document",
                      "uid": "%s",
                      "properties": {
                        "files:files": []
                      }
                    },
                    "xpath": "files:files"
                  }
                }
                """.formatted(doc.getId());
        httpClient.buildPostRequest("/upload/" + batchId + "/execute/" + AttachBlob.ID)
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
        txFeature.nextTransaction();

        doc = session.getDocument(doc.getRef());
        assertNotNull(doc.getPropertyValue("files:files"));
        assertEquals(fileName1, doc.getPropertyValue("files:files/0/file/name"));
        assertEquals(Long.parseLong(fileSize1), doc.getPropertyValue("files:files/0/file/length"));
        assertEquals(fileName2, doc.getPropertyValue("files:files/1/file/name"));
        assertEquals(Long.parseLong(fileSize2), doc.getPropertyValue("files:files/1/file/length"));
    }

    /**
     * Tests upload using file chunks.
     *
     * @since 7.4
     */
    @Test
    public void testChunkedUpload() throws IOException {

        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload chunks in desorder
        String fileName = URLEncoder.encode("Fichier accentué.txt", UTF_8);
        String mimeType = "text/plain";
        String fileContent = "Contenu accentué composé de 3 chunks";
        String fileSize = String.valueOf(getUTF8Bytes(fileContent).length);
        String chunk1 = "Contenu accentu";
        String chunk2 = "é composé de ";
        String chunk3 = "3 chunks";

        // Chunk 1
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Index", "0")
                  .addHeader("X-Upload-Chunk-Count", "3")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("X-File-Size", fileSize)
                  .addHeader("X-File-Type", mimeType)
                  .entity(chunk1)
                  .executeAndConsume(new JsonNodeHandler(SC_ACCEPTED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("chunked", node.get("uploadType").asText());
                      // TODO NXP-18247 when the actual uploaded size is returned
                      // assertEquals(String.valueOf(getUTF8Bytes(chunk1).length), node.get("uploadedSize").asText());
                      ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
                      assertEquals(1, chunkIds.size());
                      assertEquals("0", chunkIds.get(0).asText());
                      assertEquals("3", node.get("chunkCount").asText());
                  });

        // Get file info, here just to test the GET method
        httpClient.buildGetRequest("/upload/" + batchId + "/0")
                  .executeAndConsume(new JsonNodeHandler(SC_ACCEPTED), node -> {
                      assertEquals("Fichier accentué.txt", node.get("name").asText());
                      assertEquals(fileSize, node.get("size").asText());
                      assertEquals("chunked", node.get("uploadType").asText());
                      ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
                      assertEquals(1, chunkIds.size());
                      assertEquals("0", chunkIds.get(0).asText());
                      assertEquals("3", node.get("chunkCount").asText());
                  });

        // Chunk 3
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Index", "2")
                  .addHeader("X-Upload-Chunk-Count", "3")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("X-File-Size", fileSize)
                  .addHeader("X-File-Type", mimeType)
                  .entity(chunk3)
                  .executeAndConsume(new JsonNodeHandler(SC_ACCEPTED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("chunked", node.get("uploadType").asText());
                      // TODO NXP-18247 when the actual uploaded size is returned
                      // assertEquals(String.valueOf(getUTF8Bytes(chunk3).length), node.get("uploadedSize").asText());
                      ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
                      assertEquals(2, chunkIds.size());
                      assertEquals("0", chunkIds.get(0).asText());
                      assertEquals("2", chunkIds.get(1).asText());
                      assertEquals("3", node.get("chunkCount").asText());
                  });

        // Get file info, here just to test the GET method
        httpClient.buildGetRequest("/upload/" + batchId + "/0")
                  .executeAndConsume(new JsonNodeHandler(SC_ACCEPTED), node -> {
                      assertEquals("Fichier accentué.txt", node.get("name").asText());
                      assertEquals(fileSize, node.get("size").asText());
                      assertEquals("chunked", node.get("uploadType").asText());
                      ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
                      assertEquals(2, chunkIds.size());
                      assertEquals("0", chunkIds.get(0).asText());
                      assertEquals("2", chunkIds.get(1).asText());
                      assertEquals("3", node.get("chunkCount").asText());
                  });

        // Chunk 2
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Index", "1")
                  .addHeader("X-Upload-Chunk-Count", "3")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("X-File-Size", fileSize)
                  .addHeader("X-File-Type", mimeType)
                  .entity(chunk2)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("chunked", node.get("uploadType").asText());
                      // TODO NXP-18247 when the actual uploaded size is returned
                      // assertEquals(String.valueOf(getUTF8Bytes(chunk2).length), node.get("uploadedSize").asText());
                      ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
                      assertEquals(3, chunkIds.size());
                      assertEquals("0", chunkIds.get(0).asText());
                      assertEquals("1", chunkIds.get(1).asText());
                      assertEquals("2", chunkIds.get(2).asText());
                      assertEquals("3", node.get("chunkCount").asText());
                  });

        // Get file info, here just to test the GET method
        httpClient.buildGetRequest("/upload/" + batchId + "/0").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("Fichier accentué.txt", node.get("name").asText());
            assertEquals(fileSize, node.get("size").asText());
            assertEquals("chunked", node.get("uploadType").asText());
            ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
            assertEquals(3, chunkIds.size());
            assertEquals("0", chunkIds.get(0).asText());
            assertEquals("1", chunkIds.get(1).asText());
            assertEquals("2", chunkIds.get(2).asText());
            assertEquals("3", node.get("chunkCount").asText());
        });

        // Get batch info
        httpClient.buildGetRequest("/upload/" + batchId).executeAndConsume(new JsonNodeHandler(), nodes -> {
            assertEquals(1, nodes.size());
            JsonNode node = nodes.get(0);
            assertEquals("Fichier accentué.txt", node.get("name").asText());
            assertEquals(fileSize, node.get("size").asText());
            assertEquals("chunked", node.get("uploadType").asText());
            ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
            assertEquals(3, chunkIds.size());
            assertEquals("0", chunkIds.get(0).asText());
            assertEquals("1", chunkIds.get(1).asText());
            assertEquals("2", chunkIds.get(2).asText());
            assertEquals("3", node.get("chunkCount").asText());
        });

        BatchManager bm = Framework.getService(BatchManager.class);
        Blob blob = bm.getBlob(batchId, "0");
        assertNotNull(blob);
        assertEquals("Fichier accentué.txt", blob.getFilename());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals(Long.parseLong(fileSize), blob.getLength());
        assertEquals("Contenu accentué composé de 3 chunks", blob.getString());

        bm.clean(batchId);
    }

    /**
     * Tests the use of /upload using file chunks + /upload/{batchId}/{fileIdx}/execute.
     *
     * @since 7.4
     */
    @Test
    public void testBatchExecuteWithChunkedUpload() throws IOException {
        testBatchExecuteWithChunkedUpload(false);
    }

    /**
     * Tests the use of /upload using file chunks + /upload/{batchId}/{fileIdx}/execute with the "X-Batch-No-Drop"
     * header set to true.
     *
     * @since 8.4
     */
    @Test
    public void testBatchExecuteWithChunkedUploadNoDrop() throws IOException {
        testBatchExecuteWithChunkedUpload(true);
    }

    /**
     * Tests the use of /upload using file chunks + /upload/{batchId}/{fileIdx}/execute.
     *
     * @since 7.4
     */
    public void testBatchExecuteWithChunkedUpload(boolean noDrop) throws IOException {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload chunks in desorder
        String fileName = URLEncoder.encode("Fichier accentué.txt", UTF_8);
        String mimeType = "text/plain";
        String fileContent = "Contenu accentué composé de 2 chunks";
        String fileSize = String.valueOf(getUTF8Bytes(fileContent).length);
        String chunk1 = "Contenu accentué compo";
        String chunk2 = "sé de 2 chunks";

        // Chunk 2
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Index", "1")
                  .addHeader("X-Upload-Chunk-Count", "2")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("X-File-Size", fileSize)
                  .addHeader("X-File-Type", mimeType)
                  .entity(chunk2)
                  .executeAndConsume(new JsonNodeHandler(SC_ACCEPTED), node -> {
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("chunked", node.get("uploadType").asText());
                      // TODO NXP-18247 when the actual uploaded size is returned
                      // assertEquals(String.valueOf(getUTF8Bytes(chunk2).length), node.get("uploadedSize").asText());
                      ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
                      assertEquals(1, chunkIds.size());
                      assertEquals("1", chunkIds.get(0).asText());
                      assertEquals("2", node.get("chunkCount").asText());
                  });

        // Chunk 1
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Index", "0")
                  .addHeader("X-Upload-Chunk-Count", "2")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("X-File-Size", fileSize)
                  .addHeader("X-File-Type", mimeType)
                  .entity(chunk1)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("chunked", node.get("uploadType").asText());
                      // TODO NXP-18247 when the actual uploaded size is returned
                      // assertEquals(String.valueOf(getUTF8Bytes(chunk1).length), node.get("uploadedSize").asText());
                      ArrayNode chunkIds = (ArrayNode) node.get("uploadedChunkIds");
                      assertEquals(2, chunkIds.size());
                      assertEquals("0", chunkIds.get(0).asText());
                      assertEquals("1", chunkIds.get(1).asText());
                      assertEquals("2", node.get("chunkCount").asText());
                  });

        // Create a doc and attach the uploaded blob to it using the /batch/{batchId}/{fileIdx}/execute endpoint
        DocumentModel file = session.createDocumentModel("/", "testBatchExecuteDoc", "File");
        file = session.createDocument(file);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        String json = """
                {
                  "params": {
                    "document": "%s"
                  }
                }
                """.formatted(file.getPathAsString());
        var requestBuilder = httpClient.buildPostRequest("/upload/" + batchId + "/0/execute/Blob.Attach")
                                       .entity(json)
                                       .contentType(MediaType.APPLICATION_JSON);
        if (noDrop) {
            requestBuilder.addHeader("X-Batch-No-Drop", "true");
        }
        requestBuilder.executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        txFeature.nextTransaction();

        DocumentModel doc = session.getDocument(new PathRef("/testBatchExecuteDoc"));
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(blob);
        assertEquals("Fichier accentué.txt", blob.getFilename());
        assertEquals("text/plain", blob.getMimeType());
        assertEquals("Contenu accentué composé de 2 chunks", blob.getString());

        if (noDrop) {
            assertBatchExists(batchId);
        }
    }

    /**
     * We patched the Content-Type and X-File-Type header for NXP-12802 / NXP-13036
     *
     * @since 9.2
     */
    @Test
    public void testBatchUploadExecuteWithBadMimeType() throws Exception {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload file
        String fileName = URLEncoder.encode("file.pdf", UTF_8);
        String badMimeType = "pdf";
        String data = "Empty and wrong pdf data";
        String fileSize = String.valueOf(getUTF8Bytes(data).length);
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  // impossible to test a bad content-type as the client will parse it
                  // TODO used to be text/plain due to the explanation above, check if it is now possible to give empty
                  .addHeader("Content-Type", "")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("X-File-Size", fileSize)
                  .addHeader("X-File-Type", badMimeType)
                  .entity(data)
                  .execute(new VoidHandler());

        // Create a doc and attach the uploaded blob to it using the /upload/{batchId}/{fileIdx}/execute endpoint
        DocumentModel file = session.createDocumentModel("/", "testBatchExecuteDoc", "File");
        file = session.createDocument(file);
        txFeature.nextTransaction();

        String json = """
                {
                  "params": {
                    "document": "%s"
                  }
                }
                """.formatted(file.getPathAsString());
        httpClient.buildPostRequest("/upload/" + batchId + "/0/execute/Blob.Attach")
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        DocumentModel doc = session.getDocument(new PathRef("/testBatchExecuteDoc"));
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(blob);
        assertEquals("file.pdf", blob.getFilename());
        assertEquals("application/pdf", blob.getMimeType());
        assertEquals(data, blob.getString());
    }

    /**
     * @since 7.4
     */
    @Test
    public void testCancelBatch() {

        // Init batch
        String batchId = initializeNewBatch();

        // Cancel batch
        httpClient.buildDeleteRequest("/upload/" + batchId)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
    }

    /**
     * @since 7.4
     */
    @Test
    public void testEmptyResponseCases() {

        // Upload
        var statusCodeHandler = new HttpStatusCodeHandler();
        httpClient.buildPostRequest("/upload/fakeBatchId/0")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NOT_FOUND, status.intValue()));

        // Get batch info
        httpClient.buildGetRequest("/upload/fakeBatchId")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NOT_FOUND, status.intValue()));

        String batchId = initializeNewBatch();
        httpClient.buildGetRequest("/upload/" + batchId)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        // Get file info
        httpClient.buildGetRequest("/upload/fakeBatchId/0")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NOT_FOUND, status.intValue()));

        httpClient.buildGetRequest("/upload/" + batchId + "/0")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NOT_FOUND, status.intValue()));

        // Cancel batch
        httpClient.buildDeleteRequest("/upload/fakeBatchId")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    /**
     * @since 7.10
     */
    @Test
    public void testBadRequests() {
        String batchId = initializeNewBatch();

        // Bad file index
        var statusCodeHandler = new HttpStatusCodeHandler();
        httpClient.buildPostRequest("/upload/" + batchId + "/a")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_BAD_REQUEST, status.intValue()));

        // Bad chunk index
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Count", "2")
                  .addHeader("X-Upload-Chunk-Index", "a")
                  .addHeader("X-File-Size", "100")
                  .entity("chunkContent")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_BAD_REQUEST, status.intValue()));

        // Bad chunk count
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Count", "a")
                  .addHeader("X-Upload-Chunk-Index", "0")
                  .addHeader("X-File-Size", "100")
                  .entity("chunkContent")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_BAD_REQUEST, status.intValue()));

        // Bad file size
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "chunked")
                  .addHeader("X-Upload-Chunk-Count", "2")
                  .addHeader("X-Upload-Chunk-Index", "0")
                  .addHeader("X-File-Size", "a")
                  .entity("chunkContent")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_BAD_REQUEST, status.intValue()));
    }

    /**
     * @since 8.4
     */
    @Test
    public void testRemoveFile() {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        int numfiles = 5;

        var statusCodeHandler = new HttpStatusCodeHandler();
        // Upload test files
        String fileSize = String.valueOf(getUTF8Bytes("Test Content 1").length);
        for (int i = 0; i < numfiles; i++) {
            String data = "Test Content " + (i + 1);
            String fileName = URLEncoder.encode("Test File " + (i + 1) + ".txt", UTF_8);
            var requestBuilder = httpClient.buildPostRequest("/upload/" + batchId + "/" + i)
                                           .entity(data)
                                           .addHeader("X-File-Name", fileName);
            if (i == 0) {
                requestBuilder.addHeader("Content-Type", "text/plain")
                              .addHeader("X-Upload-Type", "normal")
                              .addHeader("X-File-Size", fileSize)
                              .addHeader("X-File-Type", "text/plain");
            }
            requestBuilder.executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CREATED, status.intValue()));
        }

        // Get batch info
        httpClient.buildGetRequest("/upload/" + batchId).executeAndConsume(new JsonNodeHandler(), nodes -> {
            assertEquals(numfiles, nodes.size());
            for (int i = 0; i < numfiles; i++) {
                JsonNode node = nodes.get(i);
                assertEquals("Test File " + (i + 1) + ".txt", node.get("name").asText());
                assertEquals(fileSize, node.get("size").asText());
                assertEquals("normal", node.get("uploadType").asText());
            }
        });

        // remove files #2 and #4
        httpClient.buildDeleteRequest("/upload/" + batchId + "/1")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NO_CONTENT, status.intValue()));
        httpClient.buildDeleteRequest("/upload/" + batchId + "/3")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        // check if the remaining files are the correct ones
        httpClient.buildGetRequest("/upload/" + batchId).executeAndConsume(new JsonNodeHandler(), nodes -> {
            assertEquals(numfiles - 2, nodes.size());
            JsonNode node = nodes.get(0);
            assertEquals("Test File 1.txt", node.get("name").asText());
            assertEquals(fileSize, node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
            node = nodes.get(1);
            assertEquals("Test File 3.txt", node.get("name").asText());
            assertEquals(fileSize, node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
            node = nodes.get(2);
            assertEquals("Test File 5.txt", node.get("name").asText());
            assertEquals(fileSize, node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
        });

        // test removal of invalid file index
        httpClient.buildDeleteRequest("/upload/" + batchId + "/3")
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    protected byte[] getUTF8Bytes(String data) {
        return data.getBytes(UTF_8);
    }

    protected void assertBatchExists(String batchId) {
        httpClient.buildGetRequest("/upload/" + batchId)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    protected void assertBatchNotExists(String batchId) {
        httpClient.buildGetRequest("/upload/" + batchId)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    /** @since 9.3 */
    @Test
    public void testEmptyFileUpload() {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload an empty file
        String fileName1 = URLEncoder.encode("Fichier accentué 1.txt", UTF_8);
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("X-File-Name", fileName1)
                  .addHeader("X-File-Size", "0")
                  .entity("")
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                      assertEquals("0", node.get("uploadedSize").asText());
                  });

        // Upload another empty file
        String fileName2 = "Fichier accentué 2.txt";
        httpClient.buildPostRequest("/upload/" + batchId + "/1")
                  .addHeader("X-File-Name", fileName2)
                  .addHeader("X-File-Size", "0")
                  .entity("")
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("1", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                      assertEquals("0", node.get("uploadedSize").asText());
                  });

        // Get batch info
        httpClient.buildGetRequest("/upload/" + batchId).executeAndConsume(new JsonNodeHandler(), nodes -> {
            assertEquals(2, nodes.size());
            JsonNode node = nodes.get(0);
            assertEquals("Fichier accentué 1.txt", node.get("name").asText());
            assertEquals("0", node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
            node = nodes.get(1);
            assertEquals("Fichier accentué 2.txt", node.get("name").asText());
            assertEquals("0", node.get("size").asText());
            assertEquals("normal", node.get("uploadType").asText());
        });
    }

    @Test
    public void testDefaultProviderAsLegacyFallback() {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        httpClient.buildGetRequest("/upload/" + batchId + "/info")
                  .executeAndConsume(new JsonNodeHandler(), jsonNode -> {
                      assertTrue(jsonNode.hasNonNull("provider"));
                      assertEquals(DEFAULT_BATCH_HANDLER, jsonNode.get("provider").asText());

                      JsonNode fileEntriesNode = jsonNode.get("fileEntries");
                      assertNotNull(fileEntriesNode);
                      assertTrue(fileEntriesNode.isArray());
                      ArrayNode fileEntriesArrayNode = (ArrayNode) fileEntriesNode;
                      assertEquals(0, fileEntriesArrayNode.size());

                      assertEquals(batchId, jsonNode.get("batchId").asText());
                  });
    }

    /** @since 11.1 */
    @Test
    public void testErrorOnRefreshedTokenError() {
        // The default batch handler does not support token renewal.
        String batchId = initializeNewBatchWithHandler();

        httpClient.buildPostRequest("/upload/" + batchId + "/refreshToken")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_IMPLEMENTED, status.intValue()));
    }

    /** NXP-29246: Fix import of MHTML file using Chrome */
    @Test
    public void testUploadMHTML() {
        String batchId = initializeNewBatchWithHandler();
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "multipart/related")
                  .addHeader("X-File-Name", "dummy.mhtml")
                  .entity("dummy")
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                  });
    }

    /** NXP-31123: Reject multipart uploads */
    @Test
    public void testRejectMultipartFormDataUpload() {
        httpClient.buildPostRequest("/upload/" + initializeNewBatchWithHandler() + "/0")
                  .addHeader("Content-Type", MULTIPART_FORM_DATA)
                  .entity("dummy")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_BAD_REQUEST, status.intValue()));
    }

    @Test
    public void testConflictOnCompleteUploadError() {
        httpClient.buildPostRequest("/upload/" + initializeNewBatchWithHandler() + "/0/complete")
                  .entity("{}}")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CONFLICT, status.intValue()));
    }

    @Test
    public void testBatchUploadRemoveFileEntryWithProvider() {
        String batchId = initializeNewBatchWithHandler();

        // Upload a file not in multipart
        String fileName1 = URLEncoder.encode("Fichier accentué 1.txt", UTF_8);
        String mimeType = "text/plain";
        String data1 = "Contenu accentué du premier fichier";
        String fileSize1 = String.valueOf(getUTF8Bytes(data1).length);
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName1)
                  .addHeader("X-File-Size", fileSize1)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data1)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                  });

        // Upload a file not in multipart
        String fileName2 = URLEncoder.encode("Fichier accentué 2.txt", UTF_8);
        httpClient.buildPostRequest("/upload/" + batchId + "/1")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName2)
                  .addHeader("X-File-Size", fileSize1)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data1)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("1", node.get("fileIdx").asText());
                      assertEquals("normal", node.get("uploadType").asText());
                  });

        httpClient.buildGetRequest("/upload/" + batchId + "/info").executeAndConsume(new JsonNodeHandler(), node -> {
            JsonNode fileEntriesJsonNode = node.get("fileEntries");

            assertTrue(fileEntriesJsonNode.isArray());
            ArrayNode fileEntries = (ArrayNode) fileEntriesJsonNode;
            assertEquals(2, fileEntries.size());
        });

        httpClient.buildDeleteRequest("/upload/" + batchId + "/0")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        httpClient.buildGetRequest("/upload/" + batchId + "/info").executeAndConsume(new JsonNodeHandler(), node -> {
            JsonNode fileEntriesJsonNode = node.get("fileEntries");

            assertTrue(fileEntriesJsonNode.isArray());
            ArrayNode fileEntries = (ArrayNode) fileEntriesJsonNode;
            assertEquals(1, fileEntries.size());
        });
    }

    @Test
    public void testBatchUploadWithMultivaluedBlobProperty() {

        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload a file not in multipart
        String fileName1 = URLEncoder.encode("File.txt", UTF_8);
        String mimeType = "text/plain";
        String data1 = "Content";
        String fileSize1 = String.valueOf(getUTF8Bytes(data1).length);
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("Content-Type", "text/plain")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName1)
                  .addHeader("X-File-Size", fileSize1)
                  .addHeader("X-File-Type", mimeType)
                  .entity(data1)
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                      assertEquals("true", node.get("uploaded").asText());
                      assertEquals(batchId, node.get("batchId").asText());
                      assertEquals("0", node.get("fileIdx").asText());
                  });

        String json = "{";
        json += "\"entity-type\":\"document\" ,";
        json += "\"name\":\"testBatchUploadDoc\" ,";
        json += "\"type\":\"File\" ,";
        json += "\"properties\" : {";
        json += "\"files:files\" : [ ";
        json += "{ \"file\" : { \"upload-batch\": \"" + batchId + "\", \"upload-fileId\": \"0\" }},";
        json += "{ \"file\" : { \"upload-batch\": \"" + batchId + "\", \"upload-fileId\": \"1\" }}";
        json += "]}}";

        // Assert second batch won't make the upload fail because the file does not exist
        httpClient.buildPostRequest("/path/")
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        txFeature.nextTransaction();

        DocumentModel doc = session.getDocument(new PathRef("/testBatchUploadDoc"));
        Blob blob1 = (Blob) doc.getPropertyValue("files:files/0/file");
        assertNotNull(blob1);
        assertEquals("File.txt", blob1.getFilename());
    }

    @Test
    @WithFrameworkProperty(name = NginxConstants.X_ACCEL_ENABLED, value = "true")
    public void testBatchUploadWithNginxAccel() throws Exception {
        // create a temporary file
        File txtFile = Framework.createTempFile("nginx-", ".txt");
        try (FileOutputStream fos = new FileOutputStream(txtFile)) {
            fos.write("Some content".getBytes(UTF_8));
            fos.flush();
        }

        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        // Upload a file not in multipart
        String fileName1 = URLEncoder.encode("File.txt", UTF_8);
        String mimeType = "text/plain";
        try (FileInputStream fis = new FileInputStream(txtFile)) {
            httpClient.buildPostRequest("/upload/" + batchId + "/0")
                      .addHeader("Content-Type", "text/plain")
                      .addHeader("X-Upload-Type", "normal")
                      .addHeader("X-File-Name", fileName1)
                      .addHeader("X-File-Type", mimeType)
                      .addHeader(NginxConstants.X_REQUEST_BODY_FILE_HEADER, txtFile.getAbsolutePath())
                      .addHeader(NginxConstants.X_CONTENT_MD5_HEADER, DigestUtils.md5Hex(fis))
                      .executeAndConsume(new JsonNodeHandler(SC_CREATED), node -> {
                          assertEquals("true", node.get("uploaded").asText());
                          assertEquals(batchId, node.get("batchId").asText());
                          assertEquals("0", node.get("fileIdx").asText());
                      });
        }

        // attach blob to document
        String json = """
                {
                  "entity-type": "document",
                  "name": "testBatchUploadDoc",
                  "type": "File",
                  "properties": {
                    "file:content": {
                      "upload-batch": "%s",
                      "upload-fileId": "0"
                    }
                  }
                }
                """.formatted(batchId);
        httpClient.buildPostRequest("/path/")
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        txFeature.nextTransaction();

        DocumentModel doc = session.getDocument(new PathRef("/testBatchUploadDoc"));
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(blob);
        assertEquals("File.txt", blob.getFilename());
        assertEquals("Some content", new String(blob.getByteArray(), UTF_8));
        assertNotEquals(txtFile.getAbsolutePath(), blob.getFile().getAbsolutePath());
        assertFalse(txtFile.exists());
    }

    // NXP-32107: OK case
    @Test
    public void testBatchDroppedAtDocumentCreationSuccess() throws IOException {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        var statusCodeHandler = new HttpStatusCodeHandler();
        // Upload a file in this batch
        String fileName = "Some file.txt";
        String mimeType = "text/plain";
        String content = "Some content";
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("Content-Type", mimeType)
                  .addHeader("X-File-Type", mimeType)
                  .entity(content)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CREATED, status.intValue()));
        txFeature.nextTransaction();

        // Create a document with the uploaded file as main content
        String json = getCreateDocumentJSON("File", batchId);
        httpClient.buildPostRequest("/path/")
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CREATED, status.intValue()));

        // Document creation succeeded, the batch should be dropped
        assertBatchNotExists(batchId);

        // and the file correctly attached to the document
        DocumentModel doc = session.getDocument(new PathRef("/testBatchUploadDoc"));
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(blob);
        assertEquals(fileName, blob.getFilename());
        assertEquals(mimeType, blob.getMimeType());
        assertEquals(content, new String(blob.getByteArray(), UTF_8));
    }

    // NXP-32107: KO case (DocumentValidationException)
    @Test
    public void testBatchDroppedAtDocumentCreationFailure() {
        // Get batch id, used as a session id
        String batchId = initializeNewBatch();

        var statusCodeHandler = new HttpStatusCodeHandler();
        // Upload a file in this batch
        String fileName = "Some file.txt";
        String mimeType = "text/plain";
        String content = "Some content";
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .addHeader("X-Upload-Type", "normal")
                  .addHeader("X-File-Name", fileName)
                  .addHeader("Content-Type", mimeType)
                  .addHeader("X-File-Type", mimeType)
                  .entity(content)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CREATED, status.intValue()));

        // Try to create a document with the uploaded file as main content, with an expected DocumentValidationException
        String json = getCreateDocumentJSON("ValidatedUserGroup", batchId);
        httpClient.buildPostRequest("/path/")
                  .entity(json)
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .executeAndConsume(statusCodeHandler, status ->
                  // Expect a 422 error
                  assertEquals(SC_UNPROCESSABLE_ENTITY, status.intValue()));

        // Document creation failed, the batch should NOT be dropped
        assertBatchExists(batchId);

        // and the file should be available in the batch
        httpClient.buildGetRequest("/upload/" + batchId + "/0")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(fileName, node.get("name").asText()));

        // and the document should not be created
        assertFalse(session.exists(new PathRef("/testBatchUploadDoc")));
    }

    protected String initializeNewBatch() {
        return httpClient.buildPostRequest("/upload").executeAndThen(new JsonNodeHandler(SC_CREATED), node -> {
            String batchId = node.get("batchId").asText();
            assertNotNull(batchId);
            return batchId;
        });
    }

    protected String initializeNewBatchWithHandler() {
        return httpClient.buildPostRequest("/upload/new/dummy").executeAndThen(new JsonNodeHandler(SC_OK), node -> {
            String batchId = node.get("batchId").asText();
            assertNotNull(batchId);
            return batchId;
        });
    }

    protected String getCreateDocumentJSON(String type, String batchId) {
        return "{" + //
                "  \"entity-type\":\"document\"," + //
                "  \"name\":\"testBatchUploadDoc\"," + //
                "  \"type\":\"" + type + "\"," + //
                "  \"properties\" : {" + //
                "    \"file:content\" : {" + //
                "      \"upload-batch\": \"" + batchId + "\"," + //
                "      \"upload-fileId\": \"0\"" + //
                "    }" + //
                "  }" + //
                "}";
    }

}
