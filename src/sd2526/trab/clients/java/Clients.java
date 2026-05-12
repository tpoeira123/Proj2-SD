package sd2526.trab.clients.java;


import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Users;
import sd2526.trab.clients.grpc.GrpcMessagesClient;
import sd2526.trab.clients.grpc.GrpcUsersClient;
import sd2526.trab.clients.rest.RestMessagesClient;
import sd2526.trab.clients.rest.RestUsersClient;

import java.net.URI;

public class Clients {

	public static final ClientFactory<Users> UsersClient = new GenericClientFactory<Users>( Users.SERVICE_NAME, RestUsersClient::new, GrpcUsersClient::new );

	public static final ClientFactory<Messages> MessagesClient = new GenericClientFactory<Messages>( Messages.SERVICE_NAME, RestMessagesClient::new, GrpcMessagesClient::new );
}
