package sd2526.trab.server.rest;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.clients.java.Clients;

import java.io.IOException;
import java.util.List;

@Path(RestMessages.PATH)
public class GatewayMessagesResources implements RestMessages {
//this gateway server simply forwards the clients requests and server responses,
// between the rest message client and rest message server for a specific domain
    public static String DOMAIN;

    public GatewayMessagesResources() {
    }

    private <T> T extract(Result<T> result) {
        if (result.isOK())
            return result.value();

        throw new WebApplicationException(error(result.error()));
    }

    private Response.Status error(ErrorCode errorCode){
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
    public Object postMessage(String pwd, Message msg) throws IOException {
        return extract(Clients.MessagesClient.get(DOMAIN).postMessage(pwd, msg));
    }

    // the rest messages client invokes this method
    @Override
    public Message getMessage(String name, String mid, String pwd) throws IOException {
        //the gateway invokes the true rest messages server for this specific operation,
        // and returns the result which is a Message in this case
        return extract(Clients.MessagesClient.get(DOMAIN).getInboxMessage(name, mid, pwd));
    }

    @Override
    public List<String> getMessages(String name, String pwd, String query) throws IOException {

        if (query == null || query.isBlank())
            return extract(Clients.MessagesClient.get(DOMAIN).getAllInboxMessages(name, pwd));
        else
            return extract(Clients.MessagesClient.get(DOMAIN).searchInbox(name, pwd, query));
    }

    @Override
    public void removeFromUserInbox(String name, String mid, String pwd) throws IOException {
        extract(Clients.MessagesClient.get(DOMAIN).removeInboxMessage(name, mid, pwd));
    }

    @Override
    public void deleteMessage(String name, String mid, String pwd) throws IOException {
        extract(Clients.MessagesClient.get(DOMAIN).deleteMessage(name, mid, pwd));
    }
}
