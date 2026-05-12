package sd2526.trab.clients.rest;

import java.net.URI;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;


// Copied from lab5
public class RestClient {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_SLEEP = 10000;

    private static final int READ_TIMEOUT = 10000;
    private static final int CONNECT_TIMEOUT = 10000;

    final Logger logger;

    final URI serverURI;
    final Client client;
    final ClientConfig config;

    protected WebTarget target;

    public RestClient(URI serverURI, Logger logger) {
        this.serverURI = serverURI;
        this.logger = logger;

        this.config = new ClientConfig();

        this.config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
        this.config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT);

        this.client = ClientBuilder.newClient(config);

        this.target = client.target(serverURI);
    }

    /**
     * Network call in a retry loop, because the server might br busy, so it trys MAX_RETRIES times
     * sleeping between those times
     */
    protected <T> Result<T> reTry(Supplier<Result<T>> func) {

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return func.get();
            } catch (ProcessingException x) {
                logger.info("Timeout[ try: %d : %s]".formatted(i, x.getMessage()));

                this.sleep(RETRY_SLEEP);
            } catch (Exception x) {
                x.printStackTrace();
                return Result.error(ErrorCode.INTERNAL_ERROR);
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }


    /**
     * Translates an HTTP response into a Result<T>
     *
     */
    protected <T> Result<T> processResponse(Response r, Class<T> entityType) {
        try {
            var status = r.getStatusInfo().toEnum();
            if (status == Status.OK && r.hasEntity())
                return Result.ok(r.readEntity(entityType));
            else
            if( status == Status.NO_CONTENT)
                return Result.ok();

            return Result.error(getErrorCodeFrom(status.getStatusCode()));
        } finally {
            r.close();
        }
    }

    protected <T> Result<T> processResponse(Response r, GenericType<T> entityType) {
        try {
            var status = r.getStatusInfo().toEnum();
            if (status == Status.OK && r.hasEntity())
                return Result.ok(r.readEntity(entityType));
            else
            if( status == Status.NO_CONTENT)
                return Result.ok();

            return Result.error(getErrorCodeFrom(status.getStatusCode()));
        } finally {
            r.close();
        }
    }

    protected Result<Void> processResponse(Response r) {
        try {
            var status = r.getStatusInfo().toEnum();
            if (status == Status.OK || status == Status.NO_CONTENT) {
                return Result.ok();
            }
            return Result.error(getErrorCodeFrom(status.getStatusCode()));
        } finally {
            r.close();
        }
    }


    protected static ErrorCode getErrorCodeFrom(int status) {
        return switch (status) {
            case 200, 209 -> ErrorCode.OK;
            case 409 -> ErrorCode.CONFLICT;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 400 -> ErrorCode.BAD_REQUEST;
            case 500 -> ErrorCode.INTERNAL_ERROR;
            case 501 -> ErrorCode.NOT_IMPLEMENTED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
