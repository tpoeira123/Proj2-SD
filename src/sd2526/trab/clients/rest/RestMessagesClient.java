package sd2526.trab.clients.rest;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.api.rest.RestUsers;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

public class RestMessagesClient extends RestClient implements Messages {

    private static Logger LOG = Logger.getLogger(RestUsersClient.class.getName());

    public RestMessagesClient( URI serverURI ) {
        super( serverURI, LOG);
        target = super.target.path( RestMessages.PATH );
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        return super.reTry(() -> doPostMessage(pwd, msg));
    }

    private Result<String> doPostMessage(String pwd, Message msg) {
        Response r = target.queryParam(RestMessages.PWD, pwd)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(msg, MediaType.APPLICATION_JSON));

        return super.processResponse(r, String.class);
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        return super.reTry(() -> doGetInboxMessage(name, mid, pwd));
    }

    // we don't use the .path() because the id from the InboxEntry is neither of this parameters, so, we just pass the query
    private Result<Message> doGetInboxMessage(String name, String mid, String pwd) {
        Response r = target.path(RestMessages.MBOX)
                .path(name)
                .path(mid)
                .queryParam(RestMessages.PWD, pwd)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();

        return super.processResponse(r, Message.class);
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        return super.reTry(() -> doGetAllInboxMessage(name, pwd));
    }

    private Result<List<String>> doGetAllInboxMessage(String name, String pwd) {
        Response r = target.path(RestMessages.MBOX)
                .path(name)
                .queryParam(RestMessages.PWD, pwd)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();

        return super.processResponse(r, new GenericType<>(){});
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        return super.reTry(() -> doDeleteInboxMessage(name, mid, pwd));
    }

    /**
     * ex.: http://localhost:8080/rest/messages/nuno/12345?pwd=secret
     * URL is supposed to perfectly describe exactly what you are deleting

     * target: This is your base URL (e.g., http://localhost:8080/rest/messages).
     * .path(name): the user's name to the URL (/nuno).
     * .path(mid): the message ID (/12345).
     * .queryParam(RestMessages.PWD, pwd): This adds a ? and attaches variables to the end of the URL (?pwd=secret).
     */
    private Result<Void> doDeleteInboxMessage(String name, String mid, String pwd) {
        Response r = target.path(RestMessages.MBOX)
                .path(name)
                .path(mid)
                .queryParam(RestMessages.PWD, pwd)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .delete();

        return super.processResponse(r);
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        return super.reTry(() -> doDeleteMessage(name, mid, pwd));
    }

    private Result<Void> doDeleteMessage(String name, String mid, String pwd) {
        Response r = target.path(name)
                .path(mid)
                .queryParam(RestMessages.PWD, pwd)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .delete();

        return super.processResponse(r);
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        return super.reTry(() -> doSearchInbox(name, pwd, query));
    }

    private Result<List<String>> doSearchInbox(String name, String pwd, String query) {
        Response r = target.path(RestMessages.MBOX)
                .path(name)
                .queryParam(RestMessages.PWD, pwd)
                .queryParam(RestMessages.QUERY, query)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();

        return super.processResponse(r, new GenericType<>(){});
    }
}
