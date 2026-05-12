package sd2526.trab.server.main;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Users;
import sd2526.trab.discovery.Discovery;
import sd2526.trab.server.grpc.GrpcUsersController;
import sd2526.trab.server.rest.RestUsersResource;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

public class GrpcUsersServer {
    private static Logger LOG = Logger.getLogger(GrpcUsersServer.class.getName());

    public static String DOMAIN;

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8083;
    private static final String SERVER_URI_FMT = "grpc://%s:%s/grpc";

    public static void main(String[] args) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            DOMAIN = hostname.substring(hostname.indexOf(".") + 1);

            GrpcUsersController stub = new GrpcUsersController(DOMAIN);
            ServerCredentials cred = InsecureServerCredentials.create();
            Server server = Grpc.newServerBuilderForPort(PORT, cred) .addService(stub).build();
            String serverURI = String.format(SERVER_URI_FMT, hostname, PORT);

            Discovery.getInstance().start(Users.SERVICE_NAME + "@" + DOMAIN, serverURI);

            LOG.info(String.format("Users gRPC Server ready @ %s\n", serverURI));
            server.start().awaitTermination();

        } catch(Exception e) {
            LOG.severe(e.getMessage());
        }
    }
}
