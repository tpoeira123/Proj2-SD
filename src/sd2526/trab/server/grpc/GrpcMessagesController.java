package sd2526.trab.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import sd2526.trab.api.Message;
import sd2526.trab.api.grpc.GrpcMessagesGrpc;
import sd2526.trab.api.grpc.Users;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.grpc.Messages.*;
import sd2526.trab.server.java.JavaMessages;
import sd2526.trab.server.main.GrpcMessagesServer;

import java.util.HashSet;

public class GrpcMessagesController extends GrpcController implements GrpcMessagesGrpc.AsyncService, BindableService {

    private final Messages impl;

    public GrpcMessagesController(String serverDomain) {
        this.impl = new JavaMessages(serverDomain);
    }

    @Override
    public ServerServiceDefinition bindService() {
        return GrpcMessagesGrpc.bindService(this);
    }

    private static GrpcMessage Message_to_GrpcMessage(Message m) {
        return GrpcMessage.newBuilder()
                .setId(m.getId())
                .setSender(m.getSender())
                .addAllDestination(m.getDestination())
                .setCreationTime(m.getCreationTime())
                .setSubject(m.getSubject())
                .setContents(m.getContents())
                .build();
    }

    private static Message GrpcMessage_to_Message(GrpcMessage g) {
        Message m = new Message();
        m.setId(g.getId().isEmpty() ? null : g.getId());
        m.setSender(g.getSender());
        m.setDestination(new HashSet<>(g.getDestinationList()));
        m.setCreationTime(g.getCreationTime());
        m.setSubject(g.getSubject());
        m.setContents(g.getContents());
        return m;
    }

    @Override
    public void postMessage(PostMessageArgs request, StreamObserver<PostMessageResult> responseObserver) {
        super.toGrpcResult(responseObserver,
                impl.postMessage(request.getPwd(), GrpcMessage_to_Message(request.getMessage())),
                (messageId) -> PostMessageResult.newBuilder().setMid(messageId).build());
    }

    @Override
    public void getInboxMessage(GetInboxMessageArgs request, StreamObserver<GrpcMessage> responseObserver) {
        super.toGrpcResult(responseObserver, impl.getInboxMessage(request.getName(), request.getMid(), request.getPwd()),
                (msg) -> Message_to_GrpcMessage(msg));
    }

    @Override
    public void getAllInboxMessages(GetAllInboxMessagesArgs request, StreamObserver<GetAllInboxMessagesResult> responseObserver) {
        super.toGrpcResult(responseObserver, impl.getAllInboxMessages(request.getName(), request.getPwd()),
                (ids) -> GetAllInboxMessagesResult.newBuilder().addAllMids(ids).build());
    }

    @Override
    public void removeInboxMessage(RemoveInboxMessageArgs request, StreamObserver<Empty> responseObserver) {
        super.toGrpcResult(responseObserver, impl.removeInboxMessage(request.getName(), request.getMid(), request.getPwd()),
                (v) -> Empty.getDefaultInstance());
    }

    @Override
    public void deleteMessage(DeleteMessageArgs request, StreamObserver<Empty> responseObserver) {
        super.toGrpcResult(responseObserver, impl.deleteMessage(request.getName(), request.getMid(), request.getPwd()),
                (v) -> Empty.getDefaultInstance());
    }

    @Override
    public void searchInbox(SearchInboxArgs request, StreamObserver<SearchInboxResult> responseObserver) {
        super.toGrpcResult(responseObserver, impl.searchInbox(request.getName(), request.getPwd(), request.getQuery()),
                (ids) -> SearchInboxResult.newBuilder().addAllMids(ids).build());
    }
}
