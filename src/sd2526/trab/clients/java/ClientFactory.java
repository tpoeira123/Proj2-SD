package sd2526.trab.clients.java;

import java.io.IOException;

public interface ClientFactory<T> {

	public T get(String domain) throws IOException;
}
