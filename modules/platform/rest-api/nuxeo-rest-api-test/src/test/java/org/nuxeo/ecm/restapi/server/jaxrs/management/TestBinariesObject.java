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
 *     Salem Aouana
 */

package org.nuxeo.ecm.restapi.server.jaxrs.management;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.nuxeo.ecm.core.api.Blobs.createBlob;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static org.nuxeo.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.ENTITY_FIELD_NAME;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.binary.LocalBinaryManager.DefaultBinaryGarbageCollector;
import org.nuxeo.ecm.restapi.jaxrs.io.management.BinaryManagerStatusJsonWriter;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.management.ManagementFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 11.3
 * @deprecated since 2023.9 in favor of {@link BlobsObject}
 */
@Deprecated(since = "2023.9")
@RunWith(FeaturesRunner.class)
@Features(ManagementFeature.class)
@Deploy("org.nuxeo.ecm.core.test:OSGI-INF/test-default-binary-manager.xml")
public class TestBinariesObject extends ManagementBaseTest {

    protected static final int DEFAULT_NUMBER_OF_BLOBS = 4;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected CoreSession session;

    @Test
    public void testDeleteOrphanedBinaries() {
        // Create a file document with some blobs
        DocumentModel document = session.createDocumentModel("/", "myFile", "File");

        List<Map<String, Blob>> files = new ArrayList<>(DEFAULT_NUMBER_OF_BLOBS);
        for (int num = 0; num < DEFAULT_NUMBER_OF_BLOBS; num++) {
            Map<String, Blob> map = new HashMap<>(1);
            map.put("file",
                    createBlob(String.format("Blob N%d", num), TEXT_PLAIN, UTF_8, String.format("file%d.txt", num)));
            files.add(map);
        }

        document.setPropertyValue("files:files", (Serializable) files);
        document = session.createDocument(document);
        transactionalFeature.nextTransaction();

        List<Map<String, Blob>> createdFiles = (List<Map<String, Blob>>) document.getPropertyValue("files:files");
        long sizeOfBinaries = createdFiles.stream().flatMap(m -> m.values().stream()).mapToLong(Blob::getLength).sum();

        // No orphaned binaries, no gc
        garbageCollectBinariesAndAssert(DEFAULT_NUMBER_OF_BLOBS, sizeOfBinaries, 0, 0);

        // Remove the document which will end with orphaned binaries
        session.removeDocument(document.getRef());
        transactionalFeature.nextTransaction();
        assertFalse(session.exists(document.getRef()));

        // Now the binaries are orphaned, they should be collected
        garbageCollectBinariesAndAssert(0, 0, DEFAULT_NUMBER_OF_BLOBS, sizeOfBinaries);

        // Now there is no orphaned binaries
        garbageCollectBinariesAndAssert(0, 0, 0, 0);
    }

    protected void garbageCollectBinariesAndAssert(long expectedNumBinaries, long expectedSizeBinaries,
            long expectedNumBinariesGC, long expectedSizeBinariesGC) {
        // sleep needed because the GC doesn't remove very young files
        Awaitility.await()
                  .atMost(Duration.FIVE_SECONDS)
                  .pollDelay(new Duration(DefaultBinaryGarbageCollector.TIME_RESOLUTION + 100, TimeUnit.MICROSECONDS))
                  .untilAsserted( //
                          () -> httpClient.buildDeleteRequest("/management/binaries/orphaned")
                                          .executeAndConsume(new JsonNodeHandler(), jsonNode -> {
                                              assertEquals(BinaryManagerStatusJsonWriter.ENTITY_TYPE,
                                                      jsonNode.get(ENTITY_FIELD_NAME).asText());
                                              assertEquals(expectedNumBinaries, jsonNode.get("numBinaries").asLong());
                                              assertEquals(expectedSizeBinaries, jsonNode.get("sizeBinaries").asLong());
                                              assertEquals(expectedNumBinariesGC,
                                                      jsonNode.get("numBinariesGC").asLong());
                                              assertEquals(expectedSizeBinariesGC,
                                                      jsonNode.get("sizeBinariesGC").asLong());
                                          }));
    }
}
