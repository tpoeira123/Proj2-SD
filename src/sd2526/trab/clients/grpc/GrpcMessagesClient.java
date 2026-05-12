package sd2526.trab.clients.grpc;

import sd2526.trab.api.Message;
import sd2526.trab.api.grpc.GrpcMessagesGrpc;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import sd2526.trab.api.grpc.Messages.*;

public class GrpcMessagesClient extends GrpcClient implements Messages {

    private static final Logger LOG = Logger.getLogger(GrpcMessagesClient.class.getName());
    private final GrpcMessagesGrpc.GrpcMessagesBlockingStub stub;

    public GrpcMessagesClient(URI serverURI) {
        super(serverURI);
        this.stub = GrpcMessagesGrpc.newBlockingStub(channel);
    }

    private static GrpcMessage Message_to_Grpc(Message m) {
        var b = GrpcMessage.newBuilder()
                .setSender(m.getSender())
                .addAllDestination(m.getDestination())
                .setCreationTime(m.getCreationTime())
                .setSubject(m.getSubject())
                .setContents(m.getContents());
        if (m.getId() != null) b.setId(m.getId());
        return b.build();
    }

    private static Message Grpc_to_Messages(GrpcMessage g) {
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
    public Result<String> postMessage(String pwd, Message msg) {
        return processResponse(() -> stub.postMessage(PostMessageArgs.newBuilder()
                .setPwd(pwd).setMessage(Message_to_Grpc(msg)).build()).getMid());
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        return processResponse(() ->
                Grpc_to_Messages(stub.getInboxMessage(GetInboxMessageArgs.newBuilder()
                        .setName(name).setMid(mid).setPwd(pwd).build())));
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        return processResponse(() ->
                stub.getAllInboxMessages(GetAllInboxMessagesArgs.newBuilder()
                        .setName(name).setPwd(pwd).build()).getMidsList());
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        return processResponse(() -> {
            stub.removeInboxMessage(RemoveInboxMessageArgs.newBuilder().setName(name).setMid(mid).setPwd(pwd).build());
            return null;
        });
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        return processResponse(() -> {
            stub.deleteMessage(DeleteMessageArgs.newBuilder().setName(name).setMid(mid).setPwd(pwd).build());
            return null;
        });
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        return processResponse(() -> stub.searchInbox(SearchInboxArgs.newBuilder()
                .setName(name).setPwd(pwd).setQuery(query).build()).getMidsList());
    }
}
