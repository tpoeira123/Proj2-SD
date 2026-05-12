package sd2526.trab.clients.grpc;

import java.net.URI;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.Status.Code;
import jakarta.ws.rs.ProcessingException;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;;

public class GrpcClient {

    final private URI serverURI;
    final protected Channel channel;

    protected GrpcClient(URI serverURI) {
        this.serverURI = serverURI;

        this.channel = ManagedChannelBuilder.forAddress(this.serverURI.getHost(), this.serverURI.getPort())
                .usePlaintext().enableRetry().build();
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

