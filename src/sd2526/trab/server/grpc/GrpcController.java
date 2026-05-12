package sd2526.trab.server.grpc;

import io.grpc.stub.StreamObserver;
import sd2526.trab.api.java.Result;

import java.util.function.Function;

public class GrpcController{
    protected static Throwable errorCodeToStatus( Result.ErrorCode error ) {
        var status =  switch( error) {
            case NOT_FOUND -> io.grpc.Status.NOT_FOUND;
            case CONFLICT -> io.grpc.Status.ALREADY_EXISTS;
            case FORBIDDEN -> io.grpc.Status.PERMISSION_DENIED;
            case NOT_IMPLEMENTED -> io.grpc.Status.UNIMPLEMENTED;
            case BAD_REQUEST -> io.grpc.Status.INVALID_ARGUMENT;
            default -> io.grpc.Status.INTERNAL;
        };

        return status.asException();
    }

    protected static <Q, T> void toGrpcResult(StreamObserver<T> responseObserver, Result<Q> r, Function<Q, T> f) {
        if( r.isOK() ) {
            responseObserver.onNext( f.apply( r.value()) );
            responseObserver.onCompleted();
        } else responseObserver.onError( errorCodeToStatus(r.error()));

    }
}
