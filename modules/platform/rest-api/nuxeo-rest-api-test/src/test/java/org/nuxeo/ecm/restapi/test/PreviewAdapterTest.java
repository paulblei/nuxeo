/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 *      Nelson Silva <nsilva@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 8.2
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.core.cache")
@Deploy("org.nuxeo.ecm.platform.convert")
@Deploy("org.nuxeo.ecm.platform.preview")
@Deploy("org.nuxeo.ecm.platform.htmlsanitizer")
public class PreviewAdapterTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void testFilePreview() {
        DocumentModel doc = session.createDocumentModel("/", "adoc", "File");
        Blob blob = Blobs.createBlob("Dummy txt", "text/plain", null, "dummy.txt");
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        getAndAssertPreviewStatusCode(SC_OK, doc);
        getAndAssertPreviewStatusCode(SC_OK, doc, "file:content");
    }

    @Test
    public void testFileAttachmentPreview() {
        DocumentModel doc = session.createDocumentModel("/", "adoc", "File");
        Blob attachment = Blobs.createBlob("Dummy attachment", "text/plain", null, "attachment.txt");
        List<Map<String, Serializable>> fileList = new ArrayList<>();
        fileList.add(Collections.singletonMap("file", (Serializable) attachment));
        doc.setPropertyValue("files:files", (Serializable) fileList);
        doc = session.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        getAndAssertPreviewStatusCode(SC_OK, doc, "files:files/0/file");
        // works also without schema prefix (COMPAT)
        getAndAssertPreviewStatusCode(SC_OK, doc, "files/0/file");
    }

    @Test
    public void testNotePreview() {
        DocumentModel doc = session.createDocumentModel("/", "anote", "Note");
        doc.setPropertyValue("note:note", "Dummy note");
        doc.setPropertyValue("note:mime_type", "text/html");
        doc = session.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        getAndAssertPreviewStatusCode(SC_OK, doc);
        getAndAssertPreviewStatusCode(SC_OK, doc, "note:note");
    }

    @Test
    public void testNoBlobPreview() {
        DocumentModel doc = session.createDocumentModel("/", "adoc", "File");
        doc = session.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        getAndAssertPreviewStatusCode(SC_NOT_FOUND, doc);
        getAndAssertPreviewStatusCode(SC_NOT_FOUND, doc, "file:content");
    }

    @Deploy("org.nuxeo.ecm.platform.restapi.test:preview-doctype.xml")
    @Test
    public void testNoBlobHolderPreview() {
        DocumentModel doc = session.createDocumentModel("/", "adocforpreview", "DocForPreview");
        doc = session.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        getAndAssertPreviewStatusCode(SC_NOT_FOUND, doc);
    }

    @Test
    public void testUnknownMimeTypePreview() {
        DocumentModel doc = session.createDocumentModel("/", "adoc", "File");
        Blob blob = Blobs.createBlob(new byte[] { 0 });
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        getAndAssertPreviewStatusCode(SC_NOT_FOUND, doc);
        getAndAssertPreviewStatusCode(SC_NOT_FOUND, doc, "file:content");
    }

    protected void getAndAssertPreviewStatusCode(int statusCode, DocumentModel doc) {
        getAndAssertPreviewStatusCode(statusCode, doc, null);
    }

    protected void getAndAssertPreviewStatusCode(int statusCode, DocumentModel doc, String xpath) {
        StringJoiner path = new StringJoiner("/").add("id").add(doc.getId());
        if (xpath != null) {
            path.add("@blob").add(xpath);
        }
        path.add("@preview");
        httpClient.buildGetRequest(path.toString())
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(statusCode, status.intValue()));
    }
}
