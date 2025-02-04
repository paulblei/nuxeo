/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.directory.ldap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.directory.AbstractDirectory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.DirectoryFieldMapper;
import org.nuxeo.ecm.directory.Reference;
import org.nuxeo.runtime.api.Framework;

/**
 * Implementation of the Directory interface for servers implementing the Lightweight Directory Access Protocol.
 *
 * @author ogrisel
 * @author Robert Browning
 */
public class LDAPDirectory extends AbstractDirectory {

    private static final Logger log = LogManager.getLogger(LDAPDirectory.class);

    // special field key to be able to read the DN of an LDAP entry
    public static final String DN_SPECIAL_ATTRIBUTE_KEY = "dn";

    protected Properties contextProperties;

    protected volatile SearchControls idSearchControls;

    // used in double-checked locking for lazy init
    protected volatile SearchControls searchControls;

    protected final LDAPDirectoryFactory factory;

    protected String baseFilter;

    // the following attribute is only used for testing purpose
    protected ContextProvider testServer;

    public LDAPDirectory(LDAPDirectoryDescriptor descriptor) {
        super(descriptor, LDAPReference.class);
        if (StringUtils.isEmpty(descriptor.getSearchBaseDn())) {
            throw new DirectoryException("searchBaseDn configuration is missing for directory " + getName());
        }
        factory = Framework.getService(LDAPDirectoryFactory.class);
    }

    @Override
    public LDAPDirectoryDescriptor getDescriptor() {
        return (LDAPDirectoryDescriptor) descriptor;
    }

    @Override
    protected void addReferences() {
        super.addReferences();
        // add backward compat LDAP references
        Reference[] refs = getDescriptor().getLdapReferences();
        Arrays.stream(refs).forEach(this::addReference);
    }

    @Override
    public List<Reference> getReferences(String referenceFieldName) {
        return references.get(referenceFieldName);
    }

    @Override
    public void initialize() {
        super.initialize();

        // init field mapper before search fields
        LDAPDirectoryDescriptor ldapDirectoryDesc = getDescriptor();
        fieldMapper = new DirectoryFieldMapper(ldapDirectoryDesc.fieldMapping);
        contextProperties = computeContextProperties();
        baseFilter = ldapDirectoryDesc.getAggregatedSearchFilter();

        // register the search controls after having registered the references
        // since the list of attributes to fetch my depend on registered
        // LDAPReferences
        idSearchControls = computeIdSearchControls();
        searchControls = computeSearchControls();

        log.debug("initialized LDAP directory: {} with fields: [{}] and references: [{}]", this::getName,
                () -> StringUtils.join(getSchemaFieldMap().keySet().toArray(), ", "),
                () -> StringUtils.join(references.keySet().toArray(), ", "));
    }

    /**
     * @return connection parameters to use for all LDAP queries
     */
    protected Properties computeContextProperties() {
        LDAPDirectoryDescriptor ldapDirectoryDesc = getDescriptor();
        // Initialization of LDAP connection parameters from parameters
        // registered in the LDAP "server" extension point
        Properties props = new Properties();
        LDAPServerDescriptor serverConfig = getServer();

        if (null == serverConfig) {
            throw new DirectoryException("LDAP server configuration not found: " + ldapDirectoryDesc.getServerName());
        }

        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");

        /*
         * Get initial connection URLs, dynamic URLs may cause the list to be updated when creating the session
         */
        String ldapUrls = serverConfig.getLdapUrls();
        if (ldapUrls == null) {
            throw new DirectoryException("Server LDAP URL configuration is missing for directory " + getName());
        }
        props.put(Context.PROVIDER_URL, ldapUrls);

        // define how referrals are handled
        if (!getDescriptor().getFollowReferrals()) {
            props.put(Context.REFERRAL, "ignore");
        } else {
            // this is the default mode
            props.put(Context.REFERRAL, "follow");
        }

        /*
         * SSL Connections do not work with connection timeout property
         */
        if (serverConfig.getConnectionTimeout() > -1) {
            if (!serverConfig.useSsl()) {
                props.put("com.sun.jndi.ldap.connect.timeout", Integer.toString(serverConfig.getConnectionTimeout()));
            } else {
                log.warn("SSL connections do not operate correctly"
                        + " when used with the connection timeout parameter, disabling timout");
            }
        }

        String bindDn = serverConfig.getBindDn();
        if (bindDn != null) {
            // Authenticated connection
            props.put(Context.SECURITY_PRINCIPAL, bindDn);
            props.put(Context.SECURITY_CREDENTIALS, serverConfig.getBindPassword());
        }

        if (serverConfig.isPoolingEnabled()) {
            // Enable connection pooling
            props.put("com.sun.jndi.ldap.connect.pool", "true");
            // the rest of the properties controlling pool configuration are system properties!
            setSystemProperty("com.sun.jndi.ldap.connect.pool.protocol", "plain ssl");
            setSystemProperty("com.sun.jndi.ldap.connect.pool.authentication", "none simple DIGEST-MD5");
            setSystemProperty("com.sun.jndi.ldap.connect.pool.timeout",
                    Integer.toString(serverConfig.getPoolingTimeout())); // 1 min by default
        }

        if (!serverConfig.isVerifyServerCert() && serverConfig.useSsl) {
            props.put("java.naming.ldap.factory.socket",
                    "org.nuxeo.ecm.directory.ldap.LDAPDirectory$TrustingSSLSocketFactory");
        }

        return props;
    }

    /**
     * Sets a System property, except if it's already set, to allow for external configuration.
     */
    protected void setSystemProperty(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    public Properties getContextProperties() {
        return contextProperties;
    }

    protected SearchControls computeIdSearchControls() {
        LDAPDirectoryDescriptor desc = getDescriptor();
        SearchControls scts = new SearchControls();
        scts.setSearchScope(desc.getSearchScope());
        String idAttr = fieldMapper.getBackendField(getIdField());
        scts.setReturningAttributes(new String[] { idAttr });
        scts.setCountLimit(desc.getQuerySizeLimit());
        scts.setTimeLimit(desc.getQueryTimeLimit());
        return scts;
    }

    /**
     * Search controls that only fetch attributes defined by the schema
     *
     * @return common search controls to use for all LDAP search queries
     */
    protected SearchControls computeSearchControls() {
        LDAPDirectoryDescriptor ldapDirectoryDesc = getDescriptor();
        SearchControls scts = new SearchControls();
        // respect the scope of the configuration
        scts.setSearchScope(ldapDirectoryDesc.getSearchScope());

        // only fetch attributes that are defined in the schema or needed to
        // compute LDAPReferences
        Set<String> attrs = new HashSet<>();
        for (String fieldName : getSchemaFieldMap().keySet()) {
            if (!references.containsKey(fieldName)) {
                attrs.add(fieldMapper.getBackendField(fieldName));
            }
        }
        attrs.add("objectClass");

        for (Reference reference : getReferences()) {
            if (reference instanceof LDAPReference) {
                LDAPReference ldapReference = (LDAPReference) reference;
                attrs.add(ldapReference.getStaticAttributeId(fieldMapper));
                attrs.add(ldapReference.getDynamicAttributeId());

                // Add Dynamic Reference attributes filtering
                for (LDAPDynamicReferenceDescriptor dynAtt : ldapReference.getDynamicAttributes()) {
                    attrs.add(dynAtt.baseDN);
                    attrs.add(dynAtt.filter);
                }

            }
        }

        if (getPasswordField() != null) {
            // never try to fetch the password
            attrs.remove(getPasswordField());
        }

        scts.setReturningAttributes(attrs.toArray(new String[attrs.size()]));

        scts.setCountLimit(ldapDirectoryDesc.getQuerySizeLimit());
        scts.setTimeLimit(ldapDirectoryDesc.getQueryTimeLimit());

        return scts;
    }

    /**
     * Search controls that only fetch the id.
     *
     * @since 10.3
     */
    public SearchControls getIdSearchControls() {
        return idSearchControls;
    }

    public SearchControls getSearchControls() {
        return getSearchControls(false);
    }

    public SearchControls getSearchControls(boolean fetchAllAttributes) {
        if (fetchAllAttributes) {
            // build a new ftcs instance with no attribute filtering
            LDAPDirectoryDescriptor ldapDirectoryDesc = getDescriptor();
            SearchControls scts = new SearchControls();
            scts.setSearchScope(ldapDirectoryDesc.getSearchScope());
            return scts;
        } else {
            // return the precomputed scts instance
            return searchControls;
        }
    }

    protected DirContext createContext() {
        try {
            /*
             * Dynamic server list requires re-computation on each access
             */
            String serverName = getDescriptor().getServerName();
            if (StringUtils.isEmpty(serverName)) {
                throw new DirectoryException("server configuration is missing for directory " + getName());
            }
            LDAPServerDescriptor serverConfig = getServer();
            if (serverConfig.isDynamicServerList()) {
                String ldapUrls = serverConfig.getLdapUrls();
                contextProperties.put(Context.PROVIDER_URL, ldapUrls);
            }
            return new InitialDirContext(contextProperties);
        } catch (NamingException e) {
            throw new DirectoryException("Cannot connect to LDAP directory '" + getName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * @since 5.7
     * @return ldap server descriptor bound to this directory
     */
    public LDAPServerDescriptor getServer() {
        return factory.getServer(getDescriptor().getServerName());
    }

    @Override
    public LDAPSession getSession() {
        LDAPSession session = new LDAPSession(this);
        addSession(session);
        return session;
    }

    public String getBaseFilter() {
        // NXP-2461: always add control on id field in base filter
        String idField = getIdField();
        String idAttribute = getFieldMapper().getBackendField(idField);
        String idFilter = String.format("(%s=*)", idAttribute);
        if (baseFilter != null && !"".equals(baseFilter)) {
            if (baseFilter.startsWith("(")) {
                return String.format("(&%s%s)", baseFilter, idFilter);
            } else {
                return String.format("(&(%s)%s)", baseFilter, idFilter);
            }
        } else {
            return idFilter;
        }
    }

    public String addBaseFilter(String filter) {
        List<String> filters = new ArrayList<>();
        // NXP-2461: always add control on id field in base filter
        String idFilter = '(' + getFieldMapper().getBackendField(getIdField()) + "=*)";
        filters.add(idFilter);
        if (StringUtils.isNotBlank(baseFilter)) {
            if (!baseFilter.startsWith("(")) {
                baseFilter = '(' + baseFilter + ')';
            }
            filters.add(baseFilter);
        }
        if (StringUtils.isNotBlank(filter)) {
            filters.add(filter);
        }
        return "(&" + StringUtils.join(filters, "") + ')';
    }

    protected ContextProvider getTestServer() {
        return testServer;
    }

    public void setTestServer(ContextProvider testServer) {
        this.testServer = testServer;
    }

    /**
     * SSLSocketFactory implementation that verifies all certificates.
     * <p>
     * Disabled by default but here for people to use at their own discretion.
     */
    public static class TrustingSSLSocketFactory extends SSLSocketFactory {

        private SSLSocketFactory factory;

        /**
         * Create a new SSLSocketFactory that creates a Socket regardless of the certificate used.
         */
        public TrustingSSLSocketFactory() {
            try {
                SSLContext sslContext = SSLContext.getDefault();
                sslContext.init(null, new TrustManager[] { new TrustingX509TrustManager() }, new SecureRandom());
                factory = sslContext.getSocketFactory();
            } catch (NoSuchAlgorithmException nsae) {
                throw new RuntimeException("Unable to initialize the SSL context:  ", nsae);
            } catch (KeyManagementException kme) {
                throw new RuntimeException("Unable to register a trust manager:  ", kme);
            }
        }

        /**
         * TrustingSSLSocketFactoryHolder is loaded on the first execution of TrustingSSLSocketFactory.getDefault() or
         * the first access to TrustingSSLSocketFactoryHolder.INSTANCE, not before.
         */
        private static class TrustingSSLSocketFactoryHolder {
            public static final TrustingSSLSocketFactory INSTANCE = new TrustingSSLSocketFactory();
        }

        public static SocketFactory getDefault() {
            return TrustingSSLSocketFactoryHolder.INSTANCE;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return factory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return factory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return factory.createSocket(s, host, port, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            return factory.createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return factory.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException, UnknownHostException {
            return factory.createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return factory.createSocket(address, port, localAddress, localPort);
        }

        /**
         * Insecurely trusts everyone.
         */
        private class TrustingX509TrustManager implements X509TrustManager {

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) { // NOSONAR (NXP-10253)
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) { // NOSONAR (NXP-10253)
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        }

    }

}
