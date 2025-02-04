/*
 * (C) Copyright 2023 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.auth.saml.web;

import static org.junit.Assert.assertEquals;
import static org.nuxeo.ecm.platform.auth.saml.SAMLFeature.formatXML;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.auth.saml.SAMLFeature;
import org.nuxeo.ecm.platform.auth.saml.key.KeyManagerFeature;
import org.nuxeo.ecm.platform.auth.saml.mock.MockHttpServletRequest;
import org.nuxeo.ecm.platform.auth.saml.mock.MockHttpServletResponse;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import net.shibboleth.utilities.java.support.codec.Base64Support;

/**
 * @since 2023.0
 */
@RunWith(FeaturesRunner.class)
@Features({ SAMLFeature.class, KeyManagerFeature.class })
public class MetadataServletWithKeyManagerTest {

    @Inject
    protected KeyManagerFeature keyManagerFeature;

    @Test
    public void testDoGet() throws Exception {
        var requestHandler = MockHttpServletRequest.init("GET", "http://localhost:8080/nuxeo/saml/metadata");
        var responseHandler = MockHttpServletResponse.init().withOutputStream();

        new MetadataServlet().doGet(requestHandler.mock(), responseHandler.mock());

        String encodedCertificate = Base64Support.encode(keyManagerFeature.getCertificate().getEncoded(),
                Base64Support.CHUNKED);
        var expected = """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="http://localhost:8080/login">
                  <md:SPSSODescriptor AuthnRequestsSigned="false" WantAssertionsSigned="false" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:KeyDescriptor use="signing">
                      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                        <ds:X509Data>
                          <ds:X509Certificate>%s</ds:X509Certificate>
                        </ds:X509Data>
                      </ds:KeyInfo>
                    </md:KeyDescriptor>
                    <md:KeyDescriptor use="encryption">
                      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                        <ds:X509Data>
                          <ds:X509Certificate>%s</ds:X509Certificate>
                        </ds:X509Data>
                      </ds:KeyInfo>
                    </md:KeyDescriptor>
                    <md:SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="http://localhost:8080/nuxeo/home.html"/>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</md:NameIDFormat>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:persistent</md:NameIDFormat>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified</md:NameIDFormat>
                    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName</md:NameIDFormat>
                    <md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="http://localhost:8080/nuxeo/home.html" index="0" isDefault="true"/>
                    <md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="http://localhost:8080/nuxeo/home.html" index="1" isDefault="false"/>
                  </md:SPSSODescriptor>
                </md:EntityDescriptor>
                """.formatted(
                encodedCertificate, encodedCertificate);
        assertEquals(expected, formatXML(responseHandler.getResponseString()));
    }
}
