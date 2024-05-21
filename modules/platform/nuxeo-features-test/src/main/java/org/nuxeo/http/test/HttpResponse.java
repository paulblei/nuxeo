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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * @since 2023.13
 */
public class HttpResponse {

    protected final org.apache.http.client.methods.CloseableHttpResponse response;

    protected CloningInputStream responseStream;

    protected HttpResponse(org.apache.http.client.methods.CloseableHttpResponse response) {
        this.response = response;
    }

    /**
     * @return the HTTP status code
     */
    public int getStatus() {
        return response.getStatusLine().getStatusCode();
    }

    /**
     * @return the {@link HttpHeaders#LOCATION Location} header value, if any
     */
    public URI getLocation() {
        try {
            return new URI(getFirstHeader(HttpHeaders.LOCATION));
        } catch (URISyntaxException e) {
            throw new NuxeoException("Unable to parse Location header", e);
        }
    }

    /**
     * @return the {@link HttpHeaders#CONTENT_TYPE Content-Type} header value, if any
     */
    public String getType() {
        return getFirstHeader(HttpHeaders.CONTENT_TYPE);
    }

    /**
     * @return the header value
     */
    public Optional<String> getOptFirstHeader(String headerName) {
        return Optional.ofNullable(getFirstHeader(headerName));
    }

    /**
     * @return the header value, if any
     */
    public String getFirstHeader(String headerName) {
        var header = response.getFirstHeader(headerName);
        return header == null ? null : header.getValue();
    }

    /**
     * @return the entity body as {@link InputStream}
     */
    public InputStream getEntityInputStream() {
        try {
            return getResponseStream().orElse(null);
        } catch (IOException e) {
            throw new NuxeoException("Unable to get the HTTP response entity", e);
        }
    }

    /**
     * @return the entity body as {@link String}
     */
    public String getEntityString() {
        try {
            return IOUtils.toString(getEntityInputStream(), UTF_8);
        } catch (IOException e) {
            throw new NuxeoException("Unable to read the HTTP response entity", e);
        }
    }

    protected Optional<CloningInputStream> getResponseStream() throws IOException {
        if (responseStream == null && response.getEntity() != null) {
            responseStream = new CloningInputStream(response.getEntity().getContent());
        }
        return Optional.ofNullable(responseStream);
    }
}
