package sd2526.trab.server.rest;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.rest.RestUsers;
import sd2526.trab.server.main.RestUsersServer;
import sd2526.trab.server.java.JavaUsers;

import java.util.List;
import java.util.logging.Logger;


@Path(RestUsers.PATH)
public class RestUsersResource extends RestResource implements RestUsers {

    private static Logger LOG = Logger.getLogger(RestUsersResource.class.getName());

    private final Users users;

    public RestUsersResource() {
        super();
        this.users = new JavaUsers(RestUsersServer.DOMAIN);
    }

    @Override
    public String postUser(User user) {
        LOG.info("Posting user " + user.getName());

        return super.unwrapResultOrThrow(users.postUser(user));
    }

    @Override
    public Response userExists(String name) {
        LOG.info("checking user '" + name + "' existence");

        Result<Void> result = users.userExists(name);
        if (result.isOK()) {
            return Response.noContent().build(); // HTTP 204
        }

        throw new WebApplicationException(Response.Status.NOT_FOUND);//http 404
    }

    @Override
    public User getUser(String name, String pwd) {
        LOG.info("Getting user " + name);

        return super.unwrapResultOrThrow(users.getUser(name, pwd));
    }

    @Override
    public User updateUser(String name, String pwd, User info) {
        LOG.info("updateUser called | name=" + name + " | pwd=" + pwd + " | info=" + info);

        return super.unwrapResultOrThrow(users.updateUser(name, pwd, info));
    }

    @Override
    public User deleteUser(String name, String pwd) {
        LOG.info("Deleting user " + name);
        return super.unwrapResultOrThrow(users.deleteUser(name, pwd));
    }

    @Override
    public List<User> searchUsers(String name, String pwd, String pattern) {
        LOG.info("Searching users for pattern " + pattern);

        return super.unwrapResultOrThrow(users.searchUsers(name, pwd, pattern));
    }
}
