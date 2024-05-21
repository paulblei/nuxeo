/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Kevin Leturc <kleturc@nuxeo.com>
 */

package org.nuxeo.ecm.restapi.server.jaxrs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.nuxeo.common.Environment.DISTRIBUTION_HOTFIX;
import static org.nuxeo.common.Environment.DISTRIBUTION_NAME;
import static org.nuxeo.common.Environment.DISTRIBUTION_SERVER;
import static org.nuxeo.common.Environment.DISTRIBUTION_VERSION;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.ENTITY_FIELD_NAME;
import static org.nuxeo.ecm.core.model.Repository.CAPABILITY_QUERY_BLOB_KEYS;
import static org.nuxeo.ecm.core.model.Repository.CAPABILITY_REPOSITORY;
import static org.nuxeo.runtime.capabilities.CapabilitiesServiceImpl.CAPABILITY_SERVER;
import static org.nuxeo.runtime.cluster.ClusterServiceImpl.CAPABILITY_CLUSTER;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.restapi.jaxrs.io.capabilities.CapabilitiesJsonWriter;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 11.5
 */
@RunWith(FeaturesRunner.class)
@Features(RestServerFeature.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test.test:test-cluster.xml")
public class TestCapabilitiesObject {

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    @WithFrameworkProperty(name = DISTRIBUTION_NAME, value = DISTRIBUTION_NAME)
    @WithFrameworkProperty(name = DISTRIBUTION_VERSION, value = DISTRIBUTION_VERSION)
    @WithFrameworkProperty(name = DISTRIBUTION_SERVER, value = DISTRIBUTION_SERVER)
    public void testGet() {
        httpClient.buildGetRequest("/capabilities").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(CapabilitiesJsonWriter.ENTITY_TYPE, node.get(ENTITY_FIELD_NAME).asText());

            JsonNode serverNode = node.get(CAPABILITY_SERVER);
            assertNotNull(serverNode);
            assertEquals(DISTRIBUTION_NAME, serverNode.get("distributionName").asText());
            assertEquals(DISTRIBUTION_VERSION, serverNode.get("distributionVersion").asText());
            assertEquals(DISTRIBUTION_SERVER, serverNode.get("distributionServer").asText());
            assertNull(serverNode.get("hotfixVersion"));

            JsonNode clusterNode = node.get(CAPABILITY_CLUSTER);
            assertNotNull(clusterNode);
            assertTrue(clusterNode.get("enabled").asBoolean());
            assertEquals("123", clusterNode.get("nodeId").asText());
        });
    }

    @Test
    @WithFrameworkProperty(name = DISTRIBUTION_NAME, value = DISTRIBUTION_NAME)
    @WithFrameworkProperty(name = DISTRIBUTION_VERSION, value = DISTRIBUTION_VERSION)
    @WithFrameworkProperty(name = DISTRIBUTION_SERVER, value = DISTRIBUTION_SERVER)
    @WithFrameworkProperty(name = DISTRIBUTION_HOTFIX, value = DISTRIBUTION_HOTFIX)
    public void testGetWithHotfixVersion() {
        httpClient.buildGetRequest("/capabilities").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(CapabilitiesJsonWriter.ENTITY_TYPE, node.get(ENTITY_FIELD_NAME).asText());

            JsonNode serverNode = node.get(CAPABILITY_SERVER);
            assertNotNull(serverNode);
            assertEquals(DISTRIBUTION_NAME, serverNode.get("distributionName").asText());
            assertEquals(DISTRIBUTION_VERSION, serverNode.get("distributionVersion").asText());
            assertEquals(DISTRIBUTION_SERVER, serverNode.get("distributionServer").asText());
            assertEquals(DISTRIBUTION_HOTFIX, serverNode.get("hotfixVersion").asText());

            JsonNode clusterNode = node.get(CAPABILITY_CLUSTER);
            assertNotNull(clusterNode);
            assertTrue(clusterNode.get("enabled").asBoolean());
            assertEquals("123", clusterNode.get("nodeId").asText());
        });
    }

    @Test
    public void testHasBlobKeysCapabilityDBS() {
        assumeTrue("DBS capability check", coreFeature.getStorageConfiguration().isDBS());
        assertBlobKeysCapability(true);
    }

    @Test
    @WithFrameworkProperty(name = "nuxeo.test.repository.disable.blobKeys", value = "true")
    public void testDoNotHaveBlobKeysCapabilityDBS() {
        assumeTrue("DBS capability check", coreFeature.getStorageConfiguration().isDBS());
        assertBlobKeysCapability(false);
    }

    @Test
    public void testDoNotHaveBlobKeysCapabilityVCS() {
        assumeTrue("VCS capability check", coreFeature.getStorageConfiguration().isVCS());
        assertBlobKeysCapability(false);
    }

    protected void assertBlobKeysCapability(boolean expected) {
        httpClient.buildGetRequest("/capabilities").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(CapabilitiesJsonWriter.ENTITY_TYPE, node.get(ENTITY_FIELD_NAME).asText());

            JsonNode repositoryCapabilityNode = node.get(CAPABILITY_REPOSITORY);
            assertNotNull(repositoryCapabilityNode);
            JsonNode repositoryNode = repositoryCapabilityNode.get("test");
            assertNotNull(repositoryNode);
            assertEquals(expected, repositoryNode.get(CAPABILITY_QUERY_BLOB_KEYS).asBoolean());
        });
    }
}
