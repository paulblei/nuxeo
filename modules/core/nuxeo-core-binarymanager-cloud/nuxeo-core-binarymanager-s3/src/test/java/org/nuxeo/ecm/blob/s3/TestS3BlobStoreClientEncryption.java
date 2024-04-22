/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard
 */
package org.nuxeo.ecm.blob.s3;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.KEYSTORE_FILE_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.KEYSTORE_PASS_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.PRIVKEY_ALIAS_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.PRIVKEY_PASS_PROPERTY;
import static org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration.SYSTEM_PROPERTY_PREFIX;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.blob.TestAbstractBlobStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * @since 2023.12
 */
@WithFrameworkProperty(name = SYSTEM_PROPERTY_PREFIX + "." + KEYSTORE_PASS_PROPERTY, value = "test_s3")
@WithFrameworkProperty(name = SYSTEM_PROPERTY_PREFIX + "." + PRIVKEY_ALIAS_PROPERTY, value = "test_s3")
@WithFrameworkProperty(name = SYSTEM_PROPERTY_PREFIX + "." + PRIVKEY_PASS_PROPERTY, value = "test_s3")
@Features(S3BlobProviderFeature.class)
public class TestS3BlobStoreClientEncryption extends TestAbstractBlobStore {

    @BeforeClass
    public static void beforeClass() throws URISyntaxException {
        URL url = Thread.currentThread().getContextClassLoader().getResource("test.keystore");
        Framework.getProperties()
                 .put(SYSTEM_PROPERTY_PREFIX + "." + KEYSTORE_FILE_PROPERTY, Path.of(url.toURI()).toString());
    }

    @Test
    public void testFlags() {
        assertFalse(bp.isTransactional());
        assertFalse(bp.isRecordMode());
        assertTrue(bs.getKeyStrategy().useDeDuplication());
        assertTrue(((S3BlobProvider) bp).config.useClientSideEncryption);
        BlobProvider srcProvider = blobManager.getBlobProvider("other");
        BlobStore srcStore = ((BlobStoreBlobProvider) srcProvider).store; // no need for unwrap
        assertFalse(bs.copyBlobIsOptimized(srcStore));
    }

    @Override
    public boolean checkSizeOfGCedFiles() {
        // cannot check file size with client-side encryption
        return false;
    }

    @AfterClass
    public static void afterClass() {
        Framework.getProperties().remove(SYSTEM_PROPERTY_PREFIX + "." + KEYSTORE_FILE_PROPERTY);
    }
}
