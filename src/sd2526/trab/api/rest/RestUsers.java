package sd2526.trab.api.rest;

import java.io.IOException;
import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2526.trab.api.User;

@Path(RestUsers.PATH)
public interface RestUsers {

	final String PATH = "/users";
	final String QUERY = "query";
	final String NAME = "name";
	final String PWD = "pwd";
	
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	String postUser(User user) throws IOException;

	@HEAD
	@Path("/{" + NAME +"}")
	Response userExists(@PathParam(NAME) String name) throws IOException;
	
	@GET
	@Path("/{" + NAME +"}")
	@Produces(MediaType.APPLICATION_JSON)
	User getUser(@PathParam(NAME) String name, @QueryParam(PWD) String pwd) throws IOException;
	
	@PUT
	@Path("/{" + NAME +"}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	User updateUser(@PathParam(NAME) String name, @QueryParam(PWD) String pwd, User info) throws IOException;
	
	@DELETE
	@Path("/{" + NAME +"}")
	@Produces(MediaType.APPLICATION_JSON)
	User deleteUser(@PathParam(NAME) String name, @QueryParam(PWD) String pwd) throws IOException;
	
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	List<User> searchUsers(@QueryParam(NAME) String name, @QueryParam(PWD) String pwd, @QueryParam(QUERY) String pattern) throws IOException;
}
