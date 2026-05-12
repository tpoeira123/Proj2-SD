package sd2526.trab.server.grpc;


import com.google.protobuf.Empty;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import jdk.jfr.RecordingState;
import sd2526.trab.api.User;
import sd2526.trab.api.grpc.GrpcMessagesGrpc;
import sd2526.trab.api.grpc.GrpcUsersGrpc;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.server.java.JavaUsers;
import sd2526.trab.api.grpc.Users.*;

import java.util.List;


public class GrpcUsersController extends GrpcController implements GrpcUsersGrpc.AsyncService, BindableService {

    private final Users impl;

    public GrpcUsersController(String serverDomain) {
        this.impl = new JavaUsers(serverDomain);
    }

    @Override
    public ServerServiceDefinition bindService() {
        return GrpcUsersGrpc.bindService(this);
    }


    private static GrpcUser User_to_GrpcUser(User u) {
        var builder = GrpcUser.newBuilder().setName(u.getName());
        if (u.getPwd() != null)         builder.setPwd(u.getPwd());
        if (u.getDisplayName() != null) builder.setDisplayName(u.getDisplayName());
        if (u.getDomain() != null)      builder.setDomain(u.getDomain());
        return builder.build();
    }

    private static User GrpcUser_to_User(GrpcUser g) {
        return new User(
                g.getName().isEmpty() ? null : g.getName(),
                g.hasPwd()         ? g.getPwd()         : null,
                g.hasDisplayName() ? g.getDisplayName() : null,
                g.hasDomain()      ? g.getDomain()       : null
        );
    }

    @Override
    public void postUser(sd2526.trab.api.grpc.Users.GrpcUser request, StreamObserver<sd2526.trab.api.grpc.Users.PostUserResult> responseObserver) {
        super.toGrpcResult(responseObserver, impl.postUser(GrpcUser_to_User(request)),
                (userAddress) -> PostUserResult.newBuilder().setUserAddress(userAddress).build());
    }

    @Override
    public void getUser(sd2526.trab.api.grpc.Users.GetUserArgs request, StreamObserver<sd2526.trab.api.grpc.Users.GetUserResult> responseObserver) {
        super.toGrpcResult(responseObserver, impl.getUser(request.getName(), request.getPwd()),
                (user) -> GetUserResult.newBuilder().setUser(User_to_GrpcUser(user)).build());
    }

    @Override
    public void updateUser(sd2526.trab.api.grpc.Users.UpdateUserArgs request, StreamObserver<sd2526.trab.api.grpc.Users.UpdateUserResult> responseObserver) {
        super.toGrpcResult(responseObserver, impl.updateUser(request.getName(), request.getPwd(), GrpcUser_to_User(request.getInfo())),
                (user) -> UpdateUserResult.newBuilder().setUser(User_to_GrpcUser(user)).build());
    }

    @Override
    public void deleteUser(sd2526.trab.api.grpc.Users.DeleteUserArgs request, StreamObserver<sd2526.trab.api.grpc.Users.DeleteUserResult> responseObserver) {
        super.toGrpcResult(responseObserver, impl.deleteUser(request.getName(), request.getPwd()),
                (user) -> DeleteUserResult.newBuilder().setUser(User_to_GrpcUser(user)).build());
    }

    @Override
    public void searchUsers(sd2526.trab.api.grpc.Users.SearchUsersArgs request, StreamObserver<sd2526.trab.api.grpc.Users.GrpcUser> responseObserver) {
        Result<List<User>> result = impl.searchUsers(request.getName(), request.getPwd(), request.getQuery());

        if (result.isOK()) {
            for (User u : result.value()) {
                responseObserver.onNext(User_to_GrpcUser(u));
            }
            responseObserver.onCompleted();
        } else {
            responseObserver.onError(super.errorCodeToStatus(result.error()));
        }
    }

    @Override
    public void userExists(sd2526.trab.api.grpc.Users.UserExistsArgs request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        super.toGrpcResult(responseObserver, impl.userExists(request.getName()),
                (v) -> com.google.protobuf.Empty.getDefaultInstance());
    }
}
