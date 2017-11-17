package com.sismics.docs.rest;

import com.sismics.docs.rest.util.ClientUtil;
import com.sismics.util.filter.HeaderBasedSecurityFilter;
import com.sismics.util.filter.RequestContextFilter;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.external.ExternalTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.After;
import org.junit.Before;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Base class of integration tests with Jersey.
 * 
 * @author jtremeaux
 */
public abstract class BaseJerseyTest extends JerseyTest {
    /**
     * Test HTTP server.
     */
    private HttpServer httpServer;
    
    /**
     * Utility class for the REST client.
     */
    protected ClientUtil clientUtil;

    /**
     * Test mail server.
     */
    private Wiser wiser;
    
    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new ExternalTestContainerFactory();
    }
    
    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        String travisEnv = System.getenv("TRAVIS");
        if (travisEnv == null || !travisEnv.equals("true")) {
            // Travis don't like entity dumped in the logs
            enable(TestProperties.DUMP_ENTITY);
        }
        return new Application();
    }
    
    @Override
    protected URI getBaseUri() {
        return UriBuilder.fromUri(super.getBaseUri()).path("docs").build();
    }
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("docs.header_authentication", "true");

        clientUtil = new ClientUtil(target());

        wiser = new Wiser();
        wiser.setPort(2500);
        wiser.start();

        httpServer = HttpServer.createSimpleServer(getClass().getResource("/").getFile(), "localhost", getPort());
        WebappContext context = new WebappContext("GrizzlyContext", "/docs");
        context.addFilter("requestContextFilter", RequestContextFilter.class)
                .addMappingForUrlPatterns(null, "/*");
        context.addFilter("tokenBasedSecurityFilter", TokenBasedSecurityFilter.class)
                .addMappingForUrlPatterns(null, "/*");
        context.addFilter("headerBasedSecurityFilter", HeaderBasedSecurityFilter.class)
                .addMappingForUrlPatterns(null, "/*");
        ServletRegistration reg = context.addServlet("jerseyServlet", ServletContainer.class);
        reg.setInitParameter("jersey.config.server.provider.packages", "com.sismics.docs.rest.resource");
        reg.setInitParameter("jersey.config.server.provider.classnames", "org.glassfish.jersey.media.multipart.MultiPartFeature");
        reg.setInitParameter("jersey.config.server.response.setStatusOverSendError", "true");
        reg.setLoadOnStartup(1);
        reg.addMapping("/*");
        reg.setAsyncSupported(true);
        context.deploy(httpServer);
        httpServer.start();
    }

    /**
     * Extract an email from the list and consume it.
     *
     * @return Texte de l'email
     * @throws MessagingException e
     * @throws IOException e
     */
    protected String popEmail() throws MessagingException, IOException {
        List<WiserMessage> wiserMessageList = wiser.getMessages();
        if (wiserMessageList.isEmpty()) {
            return null;
        }
        WiserMessage wiserMessage = wiserMessageList.get(wiserMessageList.size() - 1);
        wiserMessageList.remove(wiserMessageList.size() - 1);
        MimeMessage message = wiserMessage.getMimeMessage();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        message.writeTo(os);
        return os.toString();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
        if (wiser != null) {
            wiser.stop();
        }
    }
}
