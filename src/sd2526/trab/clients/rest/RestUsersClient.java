package sd2526.trab.clients.rest;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.rest.RestUsers;

public class RestUsersClient extends RestClient implements Users {

    private static Logger LOG = Logger.getLogger(RestUsersClient.class.getName());

    public RestUsersClient( URI serverURI ) {
        super( serverURI, LOG);

        target = super.target.path( RestUsers.PATH );
    }

    public Result<String> postUser(User user) {
        return super.reTry( () -> doPostUser( user ) );

    }

    private Result<String> doPostUser(User user) {
        Response r = target.request()
                .accept( MediaType.APPLICATION_JSON)
                .post(Entity.entity(user, MediaType.APPLICATION_JSON));

        return super.processResponse(r, String.class);
    }

    public Result<Void> userExists(String name) {
        return super.reTry( () -> doUserExists(name));
    }

    private Result<Void> doUserExists(String name) {
        Response r = target.path( name )
                .request()
                .head(); // we use this instead of get() because we dont need a full User in the JSON body of the HTTP response like in GET

        if (r.getStatus() == 204) {
            return Result.ok();
        }
        return Result.error(Result.ErrorCode.NOT_FOUND);//http 404
    }

    public Result<User> getUser(String name, String pwd) {
        return super.reTry( () -> doGetUser( name, pwd ));
    }

    private Result<User> doGetUser(String name, String pwd) {
        Response r = target.path( name )
                .queryParam(RestUsers.PWD, pwd).request()
                .accept(MediaType.APPLICATION_JSON)
                .get();

        return super.processResponse( r, User.class );
    }

    public Result<User> updateUser(String name, String password, User user) {
        return super.reTry(() -> doUpdateUser(name, password, user));
    }

    private Result<User> doUpdateUser(String name, String pwd, User user) {
        Response r = target.path( name )
                .queryParam(RestUsers.PWD, pwd).request()
                .accept(MediaType.APPLICATION_JSON)
                .put(Entity.entity(user, MediaType.APPLICATION_JSON));

        return super.processResponse( r, User.class );
    }

    public Result<User> deleteUser(String name, String password) {
        return super.reTry(() -> doDelete(name, password));
    }

    private Result<User> doDelete(String name, String pwd) {
        Response r = target.path( name )
                .queryParam(RestUsers.PWD, pwd).request()
                .accept(MediaType.APPLICATION_JSON)
                .delete();

        return super.processResponse( r, User.class );
    }

    @Override
    public Result<List<User>> searchUsers(String name, String pwd, String query) {
        return super.reTry(() -> doSearchUsers(name, pwd, query));
    }

    private Result<List<User>> doSearchUsers(String name,  String pwd, String query) {
        Response r = target.queryParam(RestUsers.NAME, name)
                .queryParam(RestUsers.PWD, pwd)
                .queryParam(RestUsers.QUERY, query)
                .request().accept(MediaType.APPLICATION_JSON).get();

        return super.processResponse( r, new jakarta.ws.rs.core.GenericType<>() {} );
    }
}

