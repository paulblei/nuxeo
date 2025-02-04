/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     bstefanescu, jcarsique
 */
package org.nuxeo.connect.update.task.guards;

import org.nuxeo.common.Environment;
import org.nuxeo.launcher.config.ConfigurationGenerator;

/**
 * This class can be used to check if the current platform match a given platform. For example in a command you may want
 * a guard that define the platform string format.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class PlatformHelper {

    protected final String name;

    protected final String version;

    public PlatformHelper() {
        var cg = ConfigurationGenerator.builder().init(true).build();
        name = cg.getEnv().getProperty(Environment.DISTRIBUTION_NAME);
        version = cg.getEnv().getProperty(Environment.DISTRIBUTION_VERSION);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Test whether or not the current platform is same as (or compatible) with the given one.
     *
     * @return not implemented, always return true
     */
    public boolean matches(String platform) {
        return true;
    }

    /**
     * @deprecated since 11.1, no replacement
     */
    @Deprecated(since = "11.1")
    public boolean isTomcat() {
        return true;
    }

    /**
     * @deprecated since 11.1, no replacement
     */
    @Deprecated(since = "11.1")
    public boolean isJetty() {
        return false;
    }

    public static String getFullName(String platform) {
        return null;
    }

    public static String getPlatformKey(String platform, String version) {
        return null;
    }

}
