/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 */

package org.nuxeo.targetplatforms.jaxrs;

import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.DetectThreadDeadlocksFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.webengine.test.WebEngineFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.StringHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;

@RunWith(FeaturesRunner.class)
@Features({ DetectThreadDeadlocksFeature.class, WebEngineFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.targetplatforms.core")
@Deploy("org.nuxeo.targetplatforms.core.test")
@Deploy("org.nuxeo.targetplatforms.jaxrs")
@Deploy("org.nuxeo.targetplatforms.core:OSGI-INF/test-targetplatforms-contrib.xml")
public class TargetPlatformServiceTest {

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> servletContainerFeature.getHttpUrl());

    @Ignore("NXP-17108")
    @Test
    public void ping() {
        httpClient.buildGetRequest("/target-platforms/platforms")
                  .executeAndConsume(new StringHandler(), result -> assertTrue(result.contains("nuxeo-dm-5.8")));
    }

}
