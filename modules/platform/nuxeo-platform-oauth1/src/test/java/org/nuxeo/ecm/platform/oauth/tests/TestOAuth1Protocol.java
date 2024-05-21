/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.oauth.tests;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.URIUtils;
import org.nuxeo.ecm.core.test.ServletContainerTransactionalFeature;
import org.nuxeo.ecm.platform.oauth.consumers.NuxeoOAuthConsumer;
import org.nuxeo.ecm.platform.oauth.consumers.OAuthConsumerRegistry;
import org.nuxeo.ecm.platform.oauth.tokens.OAuthToken;
import org.nuxeo.ecm.platform.oauth.tokens.OAuthTokenStore;
import org.nuxeo.ecm.platform.test.NuxeoLoginFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;

/**
 * @since 10.3
 */
@RunWith(FeaturesRunner.class)
@Features({ NuxeoLoginFeature.class, OAuth1Feature.class, ServletContainerTransactionalFeature.class })
@Deploy("org.nuxeo.ecm.platform.oauth1:OSGI-INF/test-servletcontainer-config.xml")
@Deploy("org.nuxeo.ecm.platform.oauth1:OSGI-INF/test-authentication-config.xml")
public class TestOAuth1Protocol {

    protected static final String HMAC_SHA1 = "HMAC-SHA1";

    protected static final String ENDPOINT_REQUEST_TOKEN = "/oauth/request-token";

    protected static final String ENDPOINT_AUTHORIZE = "/oauth/authorize";

    protected static final String ENDPOINT_ACCESS_TOKEN = "/oauth/access-token";

    protected static final String CONSUMER = "myconsumer";

    protected static final String CONSUMER_SECRET = "mysecret";

    protected static final String CALLBACK_URL = "http://my-site";

    protected static final String BAD_SIGNATURE = "YmFkc2ln"; // base64 for "badsig"

    @Inject
    protected OAuthConsumerRegistry consumerRegistry;

    @Inject
    protected OAuthTokenStore tokenStore;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> servletContainerFeature.getHttpUrl())
                                                                   .redirectsEnabled(false)
                                                                   .build();

    /**
     * Dummy filter that just records that it was executed.
     */
    public static class DummyFilter extends HttpFilter {

        private static final long serialVersionUID = 1L;

        public static String info;

        @Override
        public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            Principal principal = request.getUserPrincipal();
            info = principal == null ? "null" : principal.getName();
            super.doFilter(request, response, chain);
        }
    }

    protected String signURL(String method, String url, Map<String, String> params, OAuthConsumer consumer,
            String tokenSecret) throws Exception {
        OAuthMessage message = new OAuthMessage(method, url, params.entrySet(), null);
        OAuthAccessor accessor = new OAuthAccessor(consumer);
        accessor.tokenSecret = tokenSecret;
        message.sign(accessor);
        return message.getSignature();
    }

    @Test
    public void testRequestTokenBadConsumerKey() {
        httpClient.buildGetRequest(ENDPOINT_REQUEST_TOKEN)
                  .addQueryParameter("oauth_consumer_key", "nosuchconsumer")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));
    }

    @Test
    public void testRequestToken() throws Exception {
        doTestRequestToken(false);
    }

    @Test
    public void testRequestTokenBadSignature() throws Exception {
        doTestRequestToken(true);
    }

    protected void doTestRequestToken(boolean badSignature) throws Exception {
        // create consumer
        NuxeoOAuthConsumer consumer = new NuxeoOAuthConsumer(null, CONSUMER, CONSUMER_SECRET, null);
        consumerRegistry.storeConsumer(consumer);

        txFeature.nextTransaction();

        Map<String, String> params = new HashMap<>();
        params.put("oauth_consumer_key", CONSUMER);
        params.put("oauth_signature_method", HMAC_SHA1);
        params.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("oauth_nonce", "abcdefgh");
        String signature = signURL("GET", servletContainerFeature.getHttpUrl() + ENDPOINT_REQUEST_TOKEN, params,
                consumer, null);
        params.put("oauth_signature", badSignature ? BAD_SIGNATURE : signature);

        httpClient.buildGetRequest(ENDPOINT_REQUEST_TOKEN).addQueryParameters(params).executeAndConsume(response -> {
            if (badSignature) {
                assertEquals(SC_UNAUTHORIZED, response.getStatus());
            } else {
                assertEquals(SC_OK, response.getStatus());
                assertEquals("application/x-www-form-urlencoded;charset=ISO-8859-1", response.getType());
                String body = response.getEntityString();
                List<NameValuePair> res = URLEncodedUtils.parse(body, UTF_8);
                assertEquals(res.toString(), 3, res.size());
                assertEquals("oauth_token", res.get(0).getName());
                String token = res.get(0).getValue();
                assertEquals("oauth_token_secret", res.get(1).getName());
                assertEquals("oauth_callback_confirmed", res.get(2).getName());
                assertEquals("true", res.get(2).getValue()); // OAuth 1.0a
                // check request token created
                OAuthToken rToken = tokenStore.getRequestToken(token);
                assertNotNull(rToken);
            }
        });
    }

    @Test
    public void testAuthorizeGetBadToken() {
        httpClient.buildGetRequest(ENDPOINT_AUTHORIZE)
                  .addQueryParameter("oauth_token", "nosuchtoken")
                  .executeAndConsume(response -> {
                      assertEquals(SC_MOVED_TEMPORARILY, response.getStatus());
                      URI uri = response.getLocation();
                      String expectedRedir = "oauthGrant.jsp?oauth_token=nosuchtoken";
                      String expected = servletContainerFeature.getHttpUrl() + "/login.jsp?requestedUrl="
                              + URLEncoder.encode(expectedRedir, UTF_8);
                      assertEquals(expected, uri.toASCIIString());
                  });
    }

    @Test
    public void testAuthorizeGet() {
        // create request token
        OAuthToken rToken = tokenStore.createRequestToken(CONSUMER, CALLBACK_URL);

        txFeature.nextTransaction();

        httpClient.buildGetRequest(ENDPOINT_AUTHORIZE)
                  .addQueryParameter("oauth_token", rToken.getToken())
                  .executeAndConsume(response -> {
                      assertEquals(SC_MOVED_TEMPORARILY, response.getStatus());
                      URI uri = response.getLocation();
                      String expectedRedir = "oauthGrant.jsp?oauth_token=" + rToken.getToken();
                      String expected = servletContainerFeature.getHttpUrl() + "/login.jsp?requestedUrl="
                              + URLEncoder.encode(expectedRedir, UTF_8);
                      assertEquals(expected, uri.toASCIIString());
                  });
    }

    @Test
    public void testAuthorizePost() {
        // create request token
        OAuthToken rToken = tokenStore.createRequestToken(CONSUMER, CALLBACK_URL);

        txFeature.nextTransaction();

        httpClient.buildPostRequest(ENDPOINT_AUTHORIZE)
                  .addQueryParameter("oauth_token", rToken.getToken())
                  .addQueryParameter("nuxeo_login", "bob")
                  .addQueryParameter("duration", "60") // minutes
                  .executeAndConsume(response -> {
                      assertEquals(SC_MOVED_TEMPORARILY, response.getStatus());
                      URI uri = response.getLocation();

                      Map<String, String> parameters = new LinkedHashMap<>();
                      parameters.put("oauth_token", rToken.getToken());
                      parameters.put("oauth_verifier", rToken.getVerifier());
                      String expected = URIUtils.addParametersToURIQuery(CALLBACK_URL, parameters);
                      assertEquals(expected, uri.toASCIIString());
                  });

        // checks that a verifier was added to the request token
        assertNotNull(rToken.getVerifier());
        // check that the requested login was associated to the request token
        assertEquals("bob", rToken.getNuxeoLogin());
    }

    @Test
    public void testAccessToken() throws Exception {
        doTestAccessToken(false, false);
    }

    @Test
    public void testAccessTokenBadSignature() throws Exception {
        doTestAccessToken(true, false);
    }

    @Test
    public void testAccessTokenBadVerifier() throws Exception {
        doTestAccessToken(false, true);
    }

    protected void doTestAccessToken(boolean badSignature, boolean badVerifier) throws Exception {
        // create consumer
        NuxeoOAuthConsumer consumer = new NuxeoOAuthConsumer(null, CONSUMER, CONSUMER_SECRET, null);
        consumerRegistry.storeConsumer(consumer);
        // create request token
        OAuthToken rToken = tokenStore.createRequestToken(CONSUMER, CALLBACK_URL);
        // include verifier and login from authorize phase
        tokenStore.addVerifierToRequestToken(rToken.getToken(), Long.valueOf(60));
        rToken.setNuxeoLogin("bob");

        txFeature.nextTransaction();

        Map<String, String> params = new HashMap<>();
        params.put("oauth_consumer_key", CONSUMER);
        params.put("oauth_token", rToken.getToken());
        params.put("oauth_verifier", badVerifier ? "badverif" : rToken.getVerifier());
        params.put("oauth_signature_method", HMAC_SHA1);
        params.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("oauth_nonce", "123456789");
        String signature = signURL("GET", servletContainerFeature.getHttpUrl() + ENDPOINT_ACCESS_TOKEN, params,
                consumer, rToken.getTokenSecret());
        params.put("oauth_signature", badSignature ? BAD_SIGNATURE : signature);

        httpClient.buildGetRequest(ENDPOINT_ACCESS_TOKEN).addQueryParameters(params).executeAndConsume(response -> {
            if (badSignature || badVerifier) {
                assertEquals(SC_UNAUTHORIZED, response.getStatus());
            } else {
                assertEquals(SC_OK, response.getStatus());
                assertEquals("application/x-www-form-urlencoded;charset=ISO-8859-1", response.getType());
                String body = response.getEntityString();
                List<NameValuePair> res = URLEncodedUtils.parse(body, UTF_8);
                assertEquals(res.toString(), 2, res.size());
                assertEquals("oauth_token", res.get(0).getName());
                String token = res.get(0).getValue();
                assertEquals("oauth_token_secret", res.get(1).getName());
                String secret = res.get(1).getValue();
                // check request token is gone
                assertNull(tokenStore.getRequestToken(rToken.getToken()));
                // check access token exists
                OAuthToken aToken = tokenStore.getAccessToken(token);
                assertNotNull(aToken);
                assertEquals(aToken.getTokenSecret(), secret);
                assertEquals("bob", aToken.getNuxeoLogin());
            }
        });
    }

    @Test
    public void testSignedRequestTwoLegged() throws Exception {
        // create consumer
        NuxeoOAuthConsumer consumer = new NuxeoOAuthConsumer(null, CONSUMER, CONSUMER_SECRET, null);
        consumer.signedFetchSupport = "Administrator";
        consumerRegistry.storeConsumer(consumer);
        // create request token
        tokenStore.createRequestToken(CONSUMER, CALLBACK_URL);
        // two-legged: no token
        String token = "";
        String tokenSecret = null;

        doTestSignedRequest(consumer, token, tokenSecret);
    }

    @Test
    public void testSignedRequestThreeLegged() throws Exception {
        // create consumer
        NuxeoOAuthConsumer consumer = new NuxeoOAuthConsumer(null, CONSUMER, CONSUMER_SECRET, null);
        consumerRegistry.storeConsumer(consumer);
        // create request token
        OAuthToken rToken = tokenStore.createRequestToken(CONSUMER, CALLBACK_URL);
        rToken.setNuxeoLogin("Administrator"); // present in the default usermanager
        // exchange with access token
        OAuthToken aToken = tokenStore.createAccessTokenFromRequestToken(rToken);
        String token = aToken.getToken();
        String tokenSecret = aToken.getTokenSecret();

        doTestSignedRequest(consumer, token, tokenSecret);
    }

    protected void doTestSignedRequest(OAuthConsumer consumer, String token, String tokenSecret) throws Exception {
        txFeature.nextTransaction();

        DummyFilter.info = null;
        Map<String, String> params = new HashMap<>();
        params.put("oauth_consumer_key", CONSUMER);
        params.put("oauth_token", token);
        params.put("oauth_signature_method", HMAC_SHA1);
        params.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("oauth_nonce", "123456789");
        String signature = signURL("GET", servletContainerFeature.getHttpUrl() + "/somepage.html", params, consumer,
                tokenSecret);
        params.put("oauth_signature", signature);
        httpClient.buildGetRequest("/somepage.html")
                  .addQueryParameters(params)
                  .addHeader("Authorization", "OAuth")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> {
                      assertEquals(SC_NOT_FOUND, status.intValue());
                      // but the request was authenticated (our dummy filter captured the user)
                      assertEquals("Administrator", DummyFilter.info);
                  });
    }

}
