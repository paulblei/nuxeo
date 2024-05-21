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
package org.nuxeo.http.test;

import java.io.Closeable;
import java.io.IOException;

import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * @since 9.3
 * @apiNote Former {@code CloseableClientResponse} reworked for 2023.13 to be library agnostic
 * @see org.nuxeo.http.test.HttpResponse
 */
public class CloseableHttpResponse extends HttpResponse implements Closeable {

    protected CloseableHttpResponse(org.apache.http.client.methods.CloseableHttpResponse response) {
        super(response);
    }

    @Override
    public void close() {
        try {
            if (responseStream != null) {
                responseStream.forceClose();
            }
            response.close();
        } catch (IOException e) {
            throw new NuxeoException("Unable to close the HTTP response", e);
        }
    }

    @Override
    public String toString() {
        return response.toString();
    }
}
