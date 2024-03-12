package org.nuxeo.log4j;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.avro.reflect.ReflectData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nuxeo.lib.stream.codec.AvroSchemaStore;
import org.nuxeo.lib.stream.codec.FileAvroSchemaStore;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

public class TestNuxeoStreamAppender {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder(new File(FeaturesRunner.getBuildDirectory()));

    @BeforeClass
    public static void assumeKafkaEnabled() {
        // it is not possible to use the KafkaUtils.detectKafka because of log4j bootstrap
        Assume.assumeTrue("No kafka profile", Boolean.parseBoolean(System.getProperty("kafka")));
    }

    @Test
    public void testAppender() {
        // Created here to avoid log4j bootstrap
        Logger log = LogManager.getLogger(TestNuxeoStreamAppender.class);
        log.warn("Warn for testing purpose", new Throwable("here"));
        LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
        Map<String, Appender> m = logContext.getConfiguration().getAppenders();
        assertTrue(m.containsKey("STREAM"));
    }

    @Test
    public void dumpNuxeoLogEventAvroSchema() throws IOException {
        AvroSchemaStore store = new FileAvroSchemaStore(folder.newFolder().toPath());
        long fingerprint = store.addSchema(ReflectData.get().getSchema(NuxeoLogEvent.class));
        // Put a breakpoint below and copy the target/junit*/*.avsc file
        System.out.println("Schema fingerprint: " + fingerprint);
    }
}
