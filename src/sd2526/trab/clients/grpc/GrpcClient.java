package sd2526.trab.clients.grpc;

import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;

import javax.net.ssl.TrustManagerFactory;

public class GrpcClient {

    final private URI serverURI;
    final protected Channel channel;

    protected GrpcClient(URI serverURI) {
        this.serverURI = serverURI;

        this.channel = buildTlsChannel(serverURI);
    }

    private static Channel buildTlsChannel(URI serverURI) {
        try {
            String truststorePath = System.getProperty("javax.net.ssl.trustStore");
            String truststorePassword = System.getProperty("javax.net.ssl.trustStorePassword");

            KeyStore truststore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(truststorePath)) {
                truststore.load(fis, truststorePassword.toCharArray());
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(truststore);

            SslContext sslContext = GrpcSslContexts.forClient()
                    .trustManager(tmf)
                    .build();

            return NettyChannelBuilder
                    .forAddress(serverURI.getHost(), serverURI.getPort())
                    .sslContext(sslContext)
                    .enableRetry()
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build TLS gRPC channel: " + e.getMessage(), e);
        }
    }

    protected <T> Result<T> processResponse(Supplier<T> func) {
        try {
            return Result.ok(func.get());
        } catch (StatusRuntimeException sre) {
            //sre.printStackTrace();
            return Result.error(statusToErrorCode(sre.getStatus()));
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    protected static ErrorCode statusToErrorCode(Status status	) {
        return switch (status.getCode()) {
            case OK -> ErrorCode.OK;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case ALREADY_EXISTS -> ErrorCode.CONFLICT;
            case PERMISSION_DENIED -> ErrorCode.FORBIDDEN;
            case INVALID_ARGUMENT -> ErrorCode.BAD_REQUEST;
            case UNIMPLEMENTED -> ErrorCode.NOT_IMPLEMENTED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}

