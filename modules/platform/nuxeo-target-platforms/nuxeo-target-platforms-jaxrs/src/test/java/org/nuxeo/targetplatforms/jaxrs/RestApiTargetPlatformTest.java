package org.nuxeo.targetplatforms.jaxrs;

import static org.junit.Assert.assertTrue;

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

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.targetplatforms.core")
@Deploy("org.nuxeo.targetplatforms.core.test")
@Deploy("org.nuxeo.targetplatforms.jaxrs")
@Deploy("org.nuxeo.targetplatforms.core:OSGI-INF/test-targetplatforms-contrib.xml")
public class RestApiTargetPlatformTest {

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void doGetPublic() {
        httpClient.buildGetRequest("/target-platforms/public")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertTrue(node.isArray()));
    }
}
