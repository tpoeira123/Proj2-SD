package sd2526.trab.clients.grpc;

import com.google.protobuf.ByteString;
import sd2526.trab.api.User;
import sd2526.trab.api.grpc.GrpcUsersGrpc;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import sd2526.trab.api.grpc.Users.*;


public class GrpcUsersClient extends GrpcClient implements Users {

    private static final Logger LOG =  Logger.getLogger(GrpcUsersClient.class.getName());
    private final GrpcUsersGrpc.GrpcUsersBlockingStub stub;

    public GrpcUsersClient(URI serverURI) {
        super(serverURI);
        this.stub = GrpcUsersGrpc.newBlockingStub(channel);
    }


    private static User GrpcUser_to_User( GrpcUser from )  {
        return new User(
                from.getName(),
                from.hasPwd() ? from.getPwd() : null,
                from.hasDisplayName() ? from.getDisplayName() : null,
                from.hasDomain() ? from.getDomain() : null
        );
    }

    private static GrpcUser User_to_GrpcUser(User from )  {
        GrpcUser.Builder b = GrpcUser.newBuilder();

        if(from.getName() != null)
            b.setName( from.getName());

        if(from.getPwd() != null)
            b.setPwd( from.getPwd());

        if(from.getDisplayName() != null)
            b.setDisplayName( from.getDisplayName());

        if(from.getDomain() != null)
            b.setDomain( from.getDomain());

        return b.build();
    }


    @Override
    public Result<String> postUser(User user) {
        return processResponse(() -> stub.postUser(User_to_GrpcUser(user)).getUserAddress());
    }

    @Override
    public Result<User> getUser(String name, String pwd) {
        return processResponse(() -> GrpcUser_to_User(stub.getUser(GetUserArgs.newBuilder()
                .setName(name).setPwd(pwd).build()).getUser())
        );
    }

    @Override
    public Result<User> updateUser(String name, String pwd, User info) {
        return processResponse(() -> GrpcUser_to_User(stub.updateUser(UpdateUserArgs.newBuilder()
                        .setName(name).setPwd(pwd).setInfo(User_to_GrpcUser(info)).build()).getUser()));
    }

    @Override
    public Result<User> deleteUser(String name, String pwd) {
        return processResponse(() ->
                GrpcUser_to_User(stub.deleteUser(DeleteUserArgs.newBuilder()
                        .setName(name).setPwd(pwd).build()).getUser()));
    }

    @Override
    public Result<List<User>> searchUsers(String name, String pwd, String query) {
        return processResponse(() -> {
            Iterator<GrpcUser> it = stub.searchUsers(SearchUsersArgs.newBuilder()
                    .setName(name).setPwd(pwd).setQuery(query).build());

            List<User> users = new ArrayList<>();
            it.forEachRemaining(g -> users.add(GrpcUser_to_User(g)));
            return users;
        });
    }

    @Override
    public Result<Void> userExists(String name) {
        return processResponse(() -> {
            stub.userExists(UserExistsArgs.newBuilder().setName(name).build());
            return null;
        });
    }
}
