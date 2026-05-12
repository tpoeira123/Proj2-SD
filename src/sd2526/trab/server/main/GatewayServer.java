package sd2526.trab.server.main;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import sd2526.trab.discovery.Discovery;
import sd2526.trab.server.rest.GatewayMessagesResources;
import sd2526.trab.server.rest.GatewayUsersResources;
import org.glassfish.jersey.server.ResourceConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;


/**
 * Gateway is a proxy server for the rest operations, process HTTP requests and protects the server
 * Before this, we had to talk server to server directly, now, we pass everything to the gateway
 * A good analogy: GatewayServer is like a home router, it shields the server
 */
public class GatewayServer {

    private static final String GATEWAY_SERVICE = "Gateway";
    private static final int PORT = 8082;

    private static Logger LOG = Logger.getLogger(GatewayServer.class.getName());

    public static void main(String[] args) throws IOException {

        String hostName = InetAddress.getLocalHost().getHostName();
        String domain = hostName.substring(hostName.indexOf(".") + 1);
        String serverURI = String.format("http://%s:%d/rest", hostName, PORT);

        // to be able to handle both Users and Messages requests, we need to register them with the respective domain
        ResourceConfig config = new ResourceConfig();
        GatewayUsersResources.DOMAIN = domain;
        GatewayMessagesResources.DOMAIN = domain;
        config.register(GatewayUsersResources.class);
        config.register(GatewayMessagesResources.class);

        // starts listening for HTTP requests with the configuration
        JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

        LOG.info("Gateway REST Server ready @ " + serverURI);

        Discovery.getInstance().start(GATEWAY_SERVICE + "@" + domain, serverURI);

    }
}
