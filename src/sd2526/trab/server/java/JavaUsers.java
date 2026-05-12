package sd2526.trab.server.java;

import org.hibernate.exception.ConstraintViolationException;
import sd2526.trab.api.*;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.server.persistence.Hibernate;
import java.util.List;
import java.util.logging.Logger;


// This class is responsible for implementing the methods a user can do
public class JavaUsers implements Users {

    private static Logger LOG = Logger.getLogger(JavaUsers.class.getName());

    private Hibernate hibernate;
    private String serverDomain;

    public JavaUsers(String serverDomain) {
        this.hibernate = Hibernate.getInstance();
        this.serverDomain = serverDomain;
    }

    @Override
    public Result<Void> userExists(String name) {
        if (name == null || name.isBlank()) {
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        User user = hibernate.get(User.class, name);

        if (user == null) {
            return Result.error(ErrorCode.NOT_FOUND);
        }

        return Result.ok();
    }

    /**
     * Creates a new user in the local domain.
     * The operation succeeds when the name is not already in use; or, when the same user is already present in the system.
     *
     * @param user - User to be created
     * @return (OK, the user address of the user: name@domain)
     * FORBIDDEN if the domain in the user does not match the domain of the server;
     * CONFLICT if the name is already associated with a different user (i.e., password or display name differ);
     * BAD_REQUEST if the parameters are invalid (eg., null parameters, or a malformed user).
     */
    @Override
    public Result<String> postUser(User user) {
        LOG.info("postUser: " + user);

        if (user.getName() == null  || user.getName().isBlank() || user.getPwd() == null
                || user.getDisplayName() == null || user.getDisplayName().isBlank() || user.getDomain() == null || user.getDomain().isBlank()) {
            LOG.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        if (!serverDomain.equals(user.getDomain())) {
            LOG.info("Domain not match.");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        try {
            hibernate.persist(user);

            return Result.ok(user.getName() + "@" + user.getDomain());

        } catch (ConstraintViolationException ex) {
            // ConstraintViolationException is an exception triggered when we try to persist over the same ID in the database (ex: name)
            User existing = hibernate.get(User.class, user.getName());

            if (existing != null && (!existing.getPwd().equals(user.getPwd()) || !existing.getDisplayName().equals(user.getDisplayName()))) {
                LOG.info("User already exists with different password or display name.");
                return Result.error(ErrorCode.CONFLICT);
            }
            LOG.info("User already exists");
            return Result.ok(user.getName() + "@" + user.getDomain());

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

    }

    /**
     * Obtains the information on the user identified by name
     *
     * @param name - the name of the user
     * @param pwd  - password of the user
     * @return (OK, the user object, if the name exists and pwd matches the existing password)
     * FORBIDDEN if the password is incorrect or the user does not exist;
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<User> getUser(String name, String pwd) {
        LOG.info("getUser: " + name);

        if (name == null || name.isBlank() || pwd == null) {
            LOG.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        try {
            User user = hibernate.get(User.class, name);

            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }
            return Result.ok(user);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Modifies the information of a user. Values of null in any field of the user will be
     * considered as if the fields is not to be modified (the name cannot be modified).
     *
     * @param name - the name of the user
     * @param pwd  - password of the user
     * @param info - Updated information
     * @return (OK, the updated user object, if the name exists and pwd matches the
     * existing password);
     * FORBIDDEN if the password is incorrect or the user does not exist;
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<User> updateUser(String name, String pwd, User info) {
        LOG.info("updateUser: " + name);

        if (name == null || name.isBlank() || pwd == null || info == null) {
            LOG.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        // security check
        // added because I was getting an error in the test 2b)
        if(info.getName() != null) {
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        try {
            User user = hibernate.get(User.class, name);
            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            if (info.getPwd() != null) {
                user.setPwd(info.getPwd());
            }
            if (info.getDisplayName() != null) {
                user.setDisplayName(info.getDisplayName());
            }
            if (info.getDomain() != null) {
                user.setDomain(info.getDomain());
            }
            hibernate.update(user);

            return Result.ok(user);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Deletes the user identified by name.
     * All resources owned by this user in other services should also be eventually released, without waiting for completion.
     *
     * @param name - the name of the user
     * @param pwd  - password of the user
     * @return (OK, the deleted user object, if the name exists and pwd matches the existing password)
     * FORBIDDEN if the password is incorrect or the user does not exist
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<User> deleteUser(String name, String pwd) {
        LOG.info("deleteUser: " + name);
        if (name == null || name.isBlank() || pwd == null) {
            LOG.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        try {
            User user = hibernate.get(User.class, name);
            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            // TODO: Spin off a background thread here to eventually delete the user's Inbox in the Messages service.
            // TODO: Im only doing this for the first tests, the message above is for test ahead
            hibernate.delete(user);
            return Result.ok(user);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Returns the list of users for which the pattern is a substring of the name, case-insensitive.
     * The password of the users returned by the query must be set to the empty string "".
     * Only existing users can perform a search.
     *
     * @param name  - the name of the user performing the search
     * @param pwd   - password of the user
     * @param query - substring to search
     * @return (OK, list of hits, may be empty.) .
     * FORBIDDEN if the password is incorrect or the user does not exist
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<List<User>> searchUsers(String name, String pwd, String query) {
        LOG.info("searchUsers: " + name);

        if (name == null || name.isBlank() || pwd == null || query == null) {
            LOG.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        try {
            User user = hibernate.get(User.class, name);
            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            String searchQuery = "SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER('%" + query + "%')";

            // jpql works with java objects (User for examples). With SQL, we're working in the database directly
            List<User> users = hibernate.jpql(searchQuery, User.class);
            for (User u : users) {
                u.setPwd("");
            }
            return Result.ok(users);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }
}
