package sd2526.trab.clients.java;

import sd2526.trab.discovery.Discovery;

import java.io.IOException;
import java.net.URI;

/* 
 * This class exemplifies a generic client factory. 
 * Check Clients.java to see how it can be instantiated.
 * 
*/
public abstract class AbstractClientFactory<T> implements ClientFactory<T> {

	private static final String REST = "/rest";
	private static final String GRPC = "/grpc";
	private static final Object DOMAIN_DELIMITER = "@";

	private final String serviceName;
	
	public AbstractClientFactory( String serviceName) {
		this.serviceName = serviceName;
	}
	
	public T get(String domain) throws IOException {
		var sn = "%s%s%s".formatted(serviceName, DOMAIN_DELIMITER, domain);
		try {
			String uriString = Discovery.getInstance().knownUrisOf(sn);
			return newClient(URI.create(uriString));
		} catch (Exception e) {
			throw new RuntimeException("Failed to discover service URI for " + sn, e);
		}
	}
	
	private T newClient( URI serverURI ) {
		var path = serverURI.getPath();
		if (path.endsWith(REST))
			return createRestClient( serverURI );
		if (path.endsWith(GRPC))
			return createGrpcClient( serverURI );		
		throw new RuntimeException("Unknown service type..." + serverURI);	
	}
		
	abstract T createRestClient( URI serverURI );

	abstract T createGrpcClient( URI serverURI );
}
