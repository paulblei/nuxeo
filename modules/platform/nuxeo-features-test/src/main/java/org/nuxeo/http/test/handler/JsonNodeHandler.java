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

import javax.ws.rs.core.MediaType;

import org.nuxeo.http.test.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 2023.13
 */
public class JsonNodeHandler extends AbstractStatusCodeHandler<JsonNode> {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonNodeHandler() {
        super();
    }

    /**
     * @param status the status code to assert, or 0 to disable the assertion
     */
    public JsonNodeHandler(int status) {
        super(status);
    }

    @Override
    protected JsonNode doHandleResponse(HttpResponse response) throws IOException {
        String contentType = response.getType();
        if (contentType == null) {
            throw new AssertionError("HTTP Content-Type header is null, expected to start with:<application/json>");
        } else if (!contentType.startsWith(MediaType.APPLICATION_JSON)) {
            throw new AssertionError(
                    "HTTP Content-Type header mismatch, expected to start with:<application/json> but was:<"
                            + contentType + ">");
        }
        return MAPPER.readTree(response.getEntityInputStream());
    }
}
