package sd2526.trab.server.main;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import sd2526.trab.api.java.Messages;
import sd2526.trab.discovery.Discovery;
import sd2526.trab.server.grpc.GrpcMessagesController;

import java.net.InetAddress;
import java.util.logging.Logger;

public class GrpcMessagesServer {
    private static Logger LOG = Logger.getLogger(GrpcMessagesServer.class.getName());
    public static String DOMAIN;

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8084;
    private static final String SERVER_URI_FMT = "grpc://%s:%s/grpc";

    public static void main(String[] args) throws Exception {
        // args[0] = keystore path, args[1] = keystore password
        String keystorePath = args[0];
        String keystorePassword = args[1];

        System.setProperty("javax.net.ssl.trustStore", args[2]);
        System.setProperty("javax.net.ssl.trustStorePassword", args[3]);

        String hostname = InetAddress.getLocalHost().getHostName();
        DOMAIN = hostname.substring(hostname.indexOf(".") + 1);

        SslContext sslContext = GrpcUsersServer.buildSslContext(keystorePath, keystorePassword);

        GrpcMessagesController stub = new GrpcMessagesController(DOMAIN);

        Server server = NettyServerBuilder.forPort(PORT)
                .addService(stub)
                .sslContext(sslContext)
                .build();

        String serverURI = String.format(SERVER_URI_FMT, hostname, PORT);
        Discovery.getInstance().start(Messages.SERVICE_NAME + "@" + DOMAIN, serverURI);

        LOG.info(String.format("Messages gRPC Server ready @ %s\n", serverURI));
        server.start().awaitTermination();
    }
}