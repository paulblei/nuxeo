/*
 * (C) Copyright 2017-2022 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard <grenard@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 9.1
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(init = RestServerInit.class)
public class MeTest {

    private static final String DUMMY_PASSWORD = "dummy";

    private static final String NEW_PASSWORD = "newPassword";

    private static final String PASSWORD = "user1";

    private static final HttpStatusCodeHandler STATUS_CODE_HANDLER = new HttpStatusCodeHandler();

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> restServerFeature.getRestApiUrl())
                                                                   .credentials("user1", PASSWORD)
                                                                   .build();

    @Test
    public void testUserCanChangePasswordWithCorrectPassword() {
        // When I change password
        httpClient.buildPutRequest("/me/changepassword")
                  .entity("{\"oldPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}")
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a OK
                          status -> assertEquals(SC_OK, status.intValue()));

        // And I cannot access current user with old password
        httpClient.buildGetRequest("/me")
                  .executeAndConsume(STATUS_CODE_HANDLER, status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));

        // When I change I restore password using new password
        httpClient.buildPutRequest("/me/changepassword")
                  .credentials("user1", NEW_PASSWORD)
                  .entity("{\"oldPassword\": \"" + NEW_PASSWORD + "\", \"newPassword\": \"" + PASSWORD + "\"}")
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a OK
                          status -> assertEquals(SC_OK, status.intValue()));
    }

    @Test
    public void testUserCannotChangePasswordWithIncorrectPassword() {
        // When I change password
        httpClient.buildPutRequest("/me/changepassword")
                  .entity("{\"oldPassword\": \"" + DUMMY_PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}")
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a UNAUTHORIZED
                          status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));

        // And the password is unchanged and I can get current user
        httpClient.buildGetRequest("/me")
                  .executeAndConsume(STATUS_CODE_HANDLER, status -> assertEquals(SC_OK, status.intValue()));
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.test.test:test-user-manager-password-pattern-config.xml")
    public void testInvalidNewPasswordReturnsBadRequest() {
        // When I change password with one not validating the password pattern (no special char allowed)
        httpClient.buildPutRequest("/me/changepassword")
                  .entity("{\"oldPassword\": \"" + PASSWORD + "\", \"newPassword\": \"me&%\"}")
                  .executeAndConsume(STATUS_CODE_HANDLER,
                          // Then it returns a BAD_REQUEST
                          status -> assertEquals(SC_BAD_REQUEST, status.intValue()));
    }
}
