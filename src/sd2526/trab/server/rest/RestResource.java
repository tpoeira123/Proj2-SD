package sd2526.trab.server.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.java.Result;


// translates the Result.ErrorCode to HTTP errors (internet doesn't know what a Result is, so we translate)
public class RestResource {
    private static Response.Status errorCodeToStatus(Result.ErrorCode error ) {
        Response.Status status =  switch( error) {
            case NOT_FOUND -> Response.Status.NOT_FOUND;
            case CONFLICT -> Response.Status.CONFLICT;
            case FORBIDDEN -> Response.Status.FORBIDDEN;
            case NOT_IMPLEMENTED -> Response.Status.NOT_IMPLEMENTED;
            case BAD_REQUEST -> Response.Status.BAD_REQUEST;
            default -> Response.Status.INTERNAL_SERVER_ERROR;
        };

        return status;
    }


    protected static <T> T unwrapResultOrThrow( Result<T> result ) {
        if( result.isOK() )
            return result.value();
        else
            throw new WebApplicationException( errorCodeToStatus( result.error() ) );
    }
}
