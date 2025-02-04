/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.io.marshallers.json.document;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.nuxeo.common.utils.DateUtils.formatISODateTime;
import static org.nuxeo.common.utils.DateUtils.nowIfNull;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.WILDCARD_VALUE;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.io.marshallers.json.ExtensibleEntityJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.OutputStreamWithJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.MaxDepthReachedException;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.utils.DateParser;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Convert {@link DocumentModel} to Json.
 * <p>
 * This marshaller is enrichable: register class implementing {@link AbstractJsonEnricher} and managing
 * {@link DocumentModel}.
 * <p>
 * This marshaller is also extensible: extend it and simply override
 * {@link ExtensibleEntityJsonWriter#extend(Object, JsonGenerator)}.
 * <p>
 * Format is:
 *
 * <pre>
 * {
 *   "entity-type":"document",
 *   "repository": "REPOSITORY_NAME",
 *   "uid": "DOCUMENT_UID",
 *   "path": "DOCUMENT_PATH",
 *   "type": "DOCUMENT_TYPE",
 *   "facets": [ "FACET1", "FACET2", ... ],
 *   "schemas": [ {"name": SCHEMA1", "prefix": "PREFIX1"}, {"name": SCHEMA2", "prefix": "PREFIX2"}, ... ],
 *   "state": "DOCUMENT_STATE",
 *   "parentRef": "PARENT_DOCUMENT_UID",
 *   "isCheckedOut": true|false,
 *   "isRecord": true|false,
 *   "isFlexibleRecord": true|false,
 *   "retainUntil": "RETAIN_UNTIL_DATE", &lt;-- or null
 *   "retainedProperties": [ "file:content", "files:files\/*\/file"],
 *   "hasLegalHold": true|false,
 *   "isUnderRetentionOrLegalHold": true|false,
 *   "changeToken": null|"CHANGE_TOKEN",
 *   "isCheckedOut": true|false,
 *   "title": "DOCUMENT_TITLE",
 *   "lastModified": "DATE_UPDATE",  &lt;-- if dublincore is present and if dc:modified is not null
 *   "versionLabel": "DOCUMENT_VERSION",  &lt;-- only activated with parameter fetch.document=versionLabel or system property nuxeo.document.json.fetch.heavy=true
 *   "lockOwner": "LOCK_OWNER",  &lt;-- only activated if locked and with parameter fetch.document=lock or system property nuxeo.document.json.fetch.heavy=true
 *   "lockCreated": "LOCK_DATE",  &lt;-- only activated if locked and with parameter fetch.document=lock or system property nuxeo.document.json.fetch.heavy=true
 *   "properties": {   &lt;-- only present with parameter properties=schema1,schema2,... see {@link DocumentPropertyJsonWriter} for format
 *     "schemaPrefix:stringProperty": "stringPropertyValue",  &lt;-- each property may be fetched if a resolver is associated with that property and if a parameter fetch.document=propXPath is present, in this case, an object will be marshalled as value
 *     "schemaPrefix:booleanProperty": true|false,
 *     "schemaPrefix:integerProperty": 123,
 *     ...
 *     "schemaPrefix:complexProperty": {
 *        "subProperty": ...,
 *        ...
 *     },
 *     "schemaPrefix:listProperty": [
 *        ...
 *     ]
 *   }
 *             &lt;-- contextParameters if there are enrichers activated
 *             &lt;-- additional property provided by extend() method
 * }
 * </pre>
 *
 * @since 7.2
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class DocumentModelJsonWriter extends ExtensibleEntityJsonWriter<DocumentModel> {

    public static final String ENTITY_TYPE = "document";

    public static final String DOCUMENT_JSON_FETCH_HEAVY_KEY = "nuxeo.document.json.fetch.heavy";

    protected static Boolean FETCH_HEAVY_VALUES = null;

    protected static boolean fetchHeavy() {
        if (FETCH_HEAVY_VALUES == null) {
            try {
                FETCH_HEAVY_VALUES = Framework.isBooleanPropertyTrue(DOCUMENT_JSON_FETCH_HEAVY_KEY);
            } catch (Exception e) {
                FETCH_HEAVY_VALUES = false;
            }
        }
        return FETCH_HEAVY_VALUES;
    }

    protected boolean mustFetch(String name) {
        return ctx.getFetched(ENTITY_TYPE).contains(name) || fetchHeavy();
    }

    @Inject
    protected SchemaManager schemaManager;

    public DocumentModelJsonWriter() {
        super(ENTITY_TYPE, DocumentModel.class);
    }

    @Override
    protected void writeEntityBody(DocumentModel doc, JsonGenerator jg) throws IOException {
        jg.writeStringField("repository", doc.getRepositoryName());
        jg.writeStringField("uid", doc.getId());
        jg.writeStringField("path", doc.getPathAsString());
        jg.writeStringField("type", doc.getType());
        jg.writeStringField("state", doc.getRef() != null ? doc.getCurrentLifeCycleState() : null);
        jg.writeStringField("parentRef", doc.getParentRef() != null ? doc.getParentRef().toString() : null);
        jg.writeBooleanField("isCheckedOut", doc.isCheckedOut());
        boolean isRecord = doc.isRecord();
        jg.writeBooleanField("isRecord", isRecord);
        Calendar retainUntil = doc.getRetainUntil();
        jg.writeStringField("retainUntil", retainUntil == null ? null : formatISODateTime(retainUntil));
        if (isRecord) {
            jg.writeBooleanField("isFlexibleRecord", doc.isFlexibleRecord());
            jg.writeArrayFieldStart("retainedProperties");
            for (String prop : doc.getRetainedProperties()) {
                jg.writeString(schemaManager.getXPathSchemaName(prop, Set.of(doc.getSchemas())) + ":" + prop);
            }
            jg.writeEndArray();
        }
        jg.writeBooleanField("hasLegalHold", doc.hasLegalHold());
        jg.writeBooleanField("isUnderRetentionOrLegalHold", doc.isUnderRetentionOrLegalHold());
        boolean isVersion = doc.isVersion();
        jg.writeBooleanField("isVersion", isVersion);
        boolean isProxy = doc.isProxy();
        jg.writeBooleanField("isProxy", isProxy);
        if (isProxy) {
            jg.writeStringField("proxyTargetId", doc.getSourceId());
        }
        if (isVersion || isProxy) {
            jg.writeStringField("versionableId", doc.getVersionSeriesId());
        }
        jg.writeStringField("changeToken", doc.getChangeToken());
        jg.writeBooleanField("isTrashed", doc.getRef() != null && doc.isTrashed());
        jg.writeStringField("title", doc.getTitle());
        if (mustFetch("versionLabel")) {
            String versionLabel = doc.getVersionLabel();
            jg.writeStringField("versionLabel", versionLabel != null ? versionLabel : "");
        }
        if (isVersion) {
            jg.writeBooleanField("isLatestVersion", doc.isLatestVersion());
            jg.writeBooleanField("isLatestMajorVersion", doc.isLatestMajorVersion());
        }
        if (mustFetch("lock")) {
            Lock lock = doc.getLockInfo();
            if (lock != null) {
                jg.writeStringField("lockOwner", lock.getOwner());
                jg.writeStringField("lockCreated", formatISODateTime(nowIfNull(lock.getCreated())));
            }
        }
        if (doc.hasSchema("dublincore")) {
            Calendar cal = (Calendar) doc.getPropertyValue("dc:modified");
            if (cal != null) {
                jg.writeStringField("lastModified", DateParser.formatW3CDateTime(cal.getTime()));
            }
        }

        String[] docSchemas = doc.getSchemas();
        try (Closeable resource = ctx.wrap().controlDepth().open()) {
            Set<String> schemas = ctx.getProperties();
            if (!schemas.isEmpty()) {
                jg.writeObjectFieldStart("properties");
                if (schemas.contains(WILDCARD_VALUE)) {
                    // full document
                    for (String schema : docSchemas) {
                        writeSchemaProperties(jg, doc, schema);
                    }
                } else {
                    for (String schema : schemas) {
                        if (doc.hasSchema(schema)) {
                            writeSchemaProperties(jg, doc, schema);
                        }
                    }
                }
                jg.writeEndObject();
            }
        } catch (MaxDepthReachedException e) {
            // do not load properties
        }

        jg.writeArrayFieldStart("facets");
        for (String facet : doc.getFacets()) {
            jg.writeString(facet);
        }
        jg.writeEndArray();

        jg.writeArrayFieldStart("schemas");
        if (docSchemas.length > 0) {
            for (String schemaName : docSchemas) {
                Schema schema = schemaManager.getSchema(schemaName);
                if (schema != null) {
                    jg.writeStartObject();
                    String name = schema.getName();
                    String prefix = schema.getNamespace().prefix;
                    jg.writeStringField("name", name);
                    jg.writeStringField("prefix", StringUtils.isEmpty(prefix) ? name : prefix);
                    jg.writeEndObject();
                }
            }
        }
        jg.writeEndArray();

    }

    protected void writeSchemaProperties(JsonGenerator jg, DocumentModel doc, String schemaName) throws IOException {
        Writer<Property> propertyWriter = registry.getWriter(ctx, Property.class, APPLICATION_JSON_TYPE);
        // provides the current document to the property marshaller
        try (Closeable resource = ctx.wrap().with(ENTITY_TYPE, doc).open()) {
            Schema schema = schemaManager.getSchema(schemaName);
            String prefix = schema.getNamespace().prefix;
            if (prefix == null || prefix.length() == 0) {
                prefix = schemaName;
            }
            prefix = prefix + ":";
            for (Field field : schema.getFields()) {
                String prefixedName = prefix + field.getName().getLocalName();
                Property property = doc.getProperty(prefixedName);
                if (!DocumentPropertyJsonWriter.skipProperty(ctx, property)) {
                    jg.writeFieldName(prefixedName);
                    OutputStream out = new OutputStreamWithJsonWriter(jg);
                    propertyWriter.write(property, Property.class, Property.class, APPLICATION_JSON_TYPE, out);
                }
            }
        }
    }

}
