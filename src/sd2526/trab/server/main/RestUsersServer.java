package sd2526.trab.server.main;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.api.java.Users;
import sd2526.trab.discovery.Discovery;
import sd2526.trab.server.rest.RestUsersResource;

import javax.net.ssl.SSLContext;


public class RestUsersServer {

    private static Logger LOG = Logger.getLogger(RestUsersServer.class.getName());

    public static String DOMAIN;

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8080;
    public static final String SERVICE = "UsersService";
    private static final String SERVER_URI_FMT = "https://%s:%s/rest";

    public static void main(String[] args) {
        try {
            String hostname =  InetAddress.getLocalHost().getHostName();
            RestUsersServer.DOMAIN = hostname.substring(hostname.indexOf(".") + 1);

            ResourceConfig config = new ResourceConfig();
            config.register(RestUsersResource.class);

            String serverURI = String.format(SERVER_URI_FMT, hostname, PORT);
            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config, SSLContext.getDefault());

            // starts the discovery for the users server
            Discovery.getInstance().start(Users.SERVICE_NAME + "@" + DOMAIN, serverURI);

            LOG.info(String.format("%s Server ready @ %s\n",  SERVICE, serverURI));

            //More code can be executed here...
        } catch(Exception e) {
            LOG.severe(e.getMessage());
        }
    }
}


