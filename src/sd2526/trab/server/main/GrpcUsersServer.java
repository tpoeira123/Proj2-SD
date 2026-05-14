package sd2526.trab.server.main;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import sd2526.trab.api.java.Users;
import sd2526.trab.discovery.Discovery;
import sd2526.trab.server.grpc.GrpcUsersController;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.security.KeyStore;
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

    public static void main(String[] args) throws Exception {
        // Read from system properties set by the tester's flags
        String keystorePath = System.getProperty("javax.net.ssl.keyStore");
        String keystorePassword = System.getProperty("javax.net.ssl.keyStorePassword");


        String hostname = InetAddress.getLocalHost().getHostName();
        DOMAIN = hostname.substring(hostname.indexOf(".") + 1);

        SslContext sslContext = buildSslContext(keystorePath, keystorePassword);

        GrpcUsersController stub = new GrpcUsersController(DOMAIN);

        Server server = NettyServerBuilder.forPort(PORT)
                .addService(stub)
                .sslContext(sslContext)
                .build();

        String serverURI = String.format(SERVER_URI_FMT, hostname, PORT);
        Discovery.getInstance().start(Users.SERVICE_NAME + "@" + DOMAIN, serverURI);

        LOG.info(String.format("Users gRPC Server ready @ %s\n", serverURI));
        server.start().awaitTermination();
    }

    public static SslContext buildSslContext(String keystorePath, String keystorePassword) throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream input = new FileInputStream(keystorePath)) {
            keystore.load(input, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keystore, keystorePassword.toCharArray());

        return GrpcSslContexts.configure(SslContextBuilder.forServer(kmf)).build();
    }
}