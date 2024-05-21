/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 */

package org.nuxeo.ecm.restapi.server.jaxrs.management;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.jwt.JWTClaims.CLAIM_SUBJECT;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.jwt.JWTService;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 11.3
 */
@RunWith(FeaturesRunner.class)
@Features(RestServerFeature.class)
@Deploy("org.nuxeo.ecm.jwt")
@Deploy("org.nuxeo.ecm.platform.restapi.test:test-jwt-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.restapi.test:test-authentication-contrib.xml")
public class TestJWTAuthentication extends ManagementBaseTest {

    protected static final String BEARER_SP = "Bearer ";

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    @Inject
    protected JWTService jwtService;

    protected HttpClientTestRule authorizedHttpClient;

    protected HttpClientTestRule unauthorizedHttpClient;

    protected ObjectMapper mapper = new ObjectMapper();

    @Before
    public void before() {
        String authorizedUserToken = jwtService.newBuilder().withClaim(CLAIM_SUBJECT, "transient/foo").build();
        String unauthorizedUserToken = jwtService.newBuilder().withClaim(CLAIM_SUBJECT, "transient/bar").build();

        String url = String.format("http://localhost:%d/api/v1", servletContainerFeature.getPort());
        authorizedHttpClient = HttpClientTestRule.builder()
                                                 .url(url)
                                                 .accept(APPLICATION_JSON)
                                                 .header(HttpHeaders.AUTHORIZATION, BEARER_SP + authorizedUserToken)
                                                 .build();
        authorizedHttpClient.starting();
        unauthorizedHttpClient = HttpClientTestRule.builder()
                                                   .url(url)
                                                   .accept(APPLICATION_JSON)
                                                   .header(HttpHeaders.AUTHORIZATION, BEARER_SP + unauthorizedUserToken)
                                                   .build();
        unauthorizedHttpClient.starting();
    }

    @After
    public void after() {
        authorizedHttpClient.finished();
        unauthorizedHttpClient.finished();
    }

    @Test
    @WithFrameworkProperty(name = ManagementObject.MANAGEMENT_API_USER_PROPERTY, value = "transient/foo")
    public void testJWTAuthentication() {
        authorizedHttpClient.buildGetRequest("/management/distribution")
                            .executeAndConsume(new JsonNodeHandler(), node -> assertTrue(node.has("applicationName")));

        unauthorizedHttpClient.buildGetRequest("/management/distribution")
                              .executeAndConsume(new HttpStatusCodeHandler(),
                                      status -> assertEquals(HttpServletResponse.SC_FORBIDDEN, status.intValue()));
    }
}
