/*
 * (C) Copyright 2013-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.ecm.multi.tenant;

import static org.junit.Assert.assertEquals;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 5.8
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = MultiTenantRepositoryInit.class)
@Deploy("org.nuxeo.ecm.multi.tenant")
@Deploy("org.nuxeo.ecm.platform.userworkspace")
@Deploy("org.nuxeo.ecm.core.cache")
@Deploy("org.nuxeo.ecm.default.config")
@Deploy("org.nuxeo.ecm.multi.tenant:multi-tenant-test-contrib.xml")
@Deploy("org.nuxeo.ecm.multi.tenant:multi-tenant-enabled-default-test-contrib.xml")
public class TestRestAPIWithMultiTenant {

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void restAPICanAccessTenantSpecifyingDomainPart() {
        httpClient.buildGetRequest("/path/domain1/")
                  .credentials("user1", "user1")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals("/domain1", node.get("path").asText()));
    }

}
