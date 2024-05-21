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
package org.nuxeo.http.test.handler;

import java.io.IOException;

import org.apache.http.HttpStatus;
import org.nuxeo.http.test.HttpResponse;
import org.nuxeo.http.test.ResponseHandler;

/**
 * @since 2023.13
 */
public abstract class AbstractStatusCodeHandler<T> implements ResponseHandler<T> {

    protected final int status;

    public AbstractStatusCodeHandler() {
        this(HttpStatus.SC_OK);
    }

    /**
     * @param status the status code to assert, or 0 to disable the assertion
     */
    public AbstractStatusCodeHandler(int status) {
        this.status = status;
    }

    @Override
    public final T handleResponse(HttpResponse response) throws IOException {
        if (status > 0 && response.getStatus() != status) {
            throw new AssertionError("HTTP response status mismatch, expected:<" + status + "> but was:<"
                    + response.getStatus() + ">");
        }
        return doHandleResponse(response);
    }

    protected abstract T doHandleResponse(HttpResponse response) throws IOException;
}
