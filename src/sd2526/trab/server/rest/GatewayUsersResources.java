package sd2526.trab.server.rest;


import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestUsers;
import sd2526.trab.clients.java.Clients;

import java.io.IOException;
import java.util.List;

@Path(RestUsersResource.PATH)
public class GatewayUsersResources implements RestUsers {

    public static String DOMAIN;

    public GatewayUsersResources(){
    }


    private <T> T extract(Result<T> result) {
        if (result.isOK())
            return result.value();

        throw new WebApplicationException(error(result.error()));
    }

    private Response.Status error(Result.ErrorCode errorCode){
        switch (errorCode){
            case NOT_FOUND -> {
                return Response.Status.NOT_FOUND;
            }
            case FORBIDDEN -> {
                return Response.Status.FORBIDDEN;
            }
            case CONFLICT -> {
                return Response.Status.CONFLICT;
            }
            case BAD_REQUEST -> {
                return Response.Status.BAD_REQUEST;
            }
            default -> {
                return Response.Status.INTERNAL_SERVER_ERROR;
            }
        }
    }


    @Override
    public String postUser(User user) throws IOException {
        return extract(Clients.UsersClient.get(DOMAIN).postUser(user));
    }

    @Override
    public Response userExists(String name) throws IOException {
        extract(Clients.UsersClient.get(DOMAIN).userExists(name));
        return null;
    }

    @Override
    public User getUser(String name, String pwd) throws IOException {
        return extract(Clients.UsersClient.get(DOMAIN).getUser(name, pwd));
    }

    @Override
    public User updateUser(String name, String pwd, User info) throws IOException {
        // I had an error that involved something having an error in the update in case the info had a name
        // so, we set the info name to null
        User stripped = new User(null, info.getPwd(), info.getDisplayName(), info.getDomain());
        return extract(Clients.UsersClient.get(DOMAIN).updateUser(name, pwd, stripped));
    }

    @Override
    public User deleteUser(String name, String pwd) throws IOException {
        return extract(Clients.UsersClient.get(DOMAIN).deleteUser(name, pwd));
    }

    @Override
    public List<User> searchUsers(String name, String pwd, String pattern) throws IOException {
        return extract(Clients.UsersClient.get(DOMAIN).searchUsers(name, pwd, pattern));
    }
}
