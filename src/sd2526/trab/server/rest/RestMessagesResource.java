package sd2526.trab.server.rest;

import jakarta.ws.rs.Path;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.server.main.RestMessagesServer;
import sd2526.trab.server.java.JavaMessages;
import java.util.List;
import java.util.logging.Logger;


@Path(RestMessages.PATH)
public class RestMessagesResource extends RestResource implements RestMessages {

    private static Logger LOG = Logger.getLogger(RestMessagesResource.class.getName());

    private final Messages message;

    public RestMessagesResource() {
        super();
        message = new JavaMessages(RestMessagesServer.DOMAIN);
    }

    @Override
    public String postMessage(String pwd, Message msg) {
        LOG.info("postMessage: " + msg);

        return super.unwrapResultOrThrow(message.postMessage(pwd, msg));
    }

    @Override
    public Message getMessage(String name, String mid, String pwd) {
        LOG.info("getMessage: " + mid);

        return super.unwrapResultOrThrow(message.getInboxMessage(name, mid, pwd));
    }

    @Override
    public List<String> getMessages(String name, String pwd, String query) {
        LOG.info("getMessages: " + query);

        if (query == null || query.isBlank())
            return super.unwrapResultOrThrow(message.getAllInboxMessages(name, pwd));
        else
            return super.unwrapResultOrThrow(message.searchInbox(name, pwd, query));
    }

    @Override
    public void removeFromUserInbox(String name, String mid, String pwd) {
        LOG.info("removeFromUserInbox: " + mid);

        super.unwrapResultOrThrow(message.removeInboxMessage(name, mid, pwd));
    }

    @Override
    public void deleteMessage(String name, String mid, String pwd) {
        super.unwrapResultOrThrow(message.deleteMessage(name, mid, pwd));
    }
}
