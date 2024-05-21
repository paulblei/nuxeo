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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.http.test;

import java.io.Closeable;
import java.io.IOException;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.HttpUriRequest;

/**
 * @since 2023.13
 */
record HttpClientExecution(HttpUriRequest request, CloseableHttpResponse response) implements Closeable {
    @Override
    public void close() throws IOException {
        if (request instanceof HttpEntityEnclosingRequest enclosingRequest && enclosingRequest.getEntity() != null
                && enclosingRequest.getEntity().getContent() instanceof CloningInputStream cloningStream) {
            cloningStream.forceClose();
        }
        response.close();
    }
}
