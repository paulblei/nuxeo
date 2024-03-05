/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.api.impl.blob;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Blob backed by a URL. Note that the encoding is not detected even for an HTTP URL.
 */
public class URLBlob extends AbstractBlob implements Serializable {

    private static final Logger log = LogManager.getLogger(URLBlob.class);

    private static final long serialVersionUID = 1L;

    protected final URL url;

    protected volatile Long length = null;

    public URLBlob(URL url) {
        this(url, null, null);
    }

    public URLBlob(URL url, String mimeType) {
        this(url, mimeType, null);
    }

    public URLBlob(URL url, String mimeType, String encoding) {
        if (url == null) {
            throw new NullPointerException("null url");
        }
        this.url = url;
        this.mimeType = mimeType;
        this.encoding = encoding;
    }

    @Override
    public InputStream getStream() throws IOException {
        return url.openStream();
    }

    @Override
    public long getLength() {
        if (this.length == null) {
            synchronized (this) {
                if (this.length == null) {
                    // Content-Length from URL connection is not reliable
                    try (InputStream in = getStream()) {
                        this.length = in.transferTo(NULL_OUTPUT_STREAM);
                    } catch (IOException e) {
                        log.error("Cannot get blob length for url: " + url, e);
                        this.length = -1L;
                    }
                }
            }
        }
        return this.length;
    }

}
