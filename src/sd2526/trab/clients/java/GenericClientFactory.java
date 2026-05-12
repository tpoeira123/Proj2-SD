package sd2526.trab.clients.java;

import sd2526.trab.discovery.Discovery;

import java.net.URI;
import java.util.function.Function;

/* 
 * This class exemplifies a generic client factory that leverages functional interfaces.
 * Check Clients.java to see how it can be instantiated.
*/
public class GenericClientFactory<T> implements ClientFactory<T> {

	private static final String REST = "/rest";
	private static final String GRPC = "/grpc";
	private static final Object DOMAIN_DELIMITER = "@";

	private final String serviceName;
	private final Function<URI, T> restClientFunc;
	private final Function<URI, T> grpcClientFunc;
	
	
	GenericClientFactory( String serviceName, Function<URI, T> restClientFunc, Function<URI, T> grpcClientFunc) {
		this.restClientFunc = restClientFunc;
		this.grpcClientFunc = grpcClientFunc;
		this.serviceName = serviceName;
	}
	
	
	@Override
	public T get(String domain) {
		var sn = "%s%s%s".formatted(serviceName, DOMAIN_DELIMITER, domain);
		try {
			String uriString = Discovery.getInstance().knownUrisOf(sn);
			return newClient(URI.create(uriString));
		} catch (Exception e) {
			throw new RuntimeException("Failed to discover service URI for " + sn, e);
		}
	}
	
	private T newClient( URI serverURI ) {
		// this defines if this client uses REST or gRPC based on the final part of the endpoint path (/rest for REST and /grpc for gRPC)
		// o endpoint está codificado na variável de instância serviceName desta classe
		var path = serverURI.getPath();
		if (path.endsWith(REST))
			return restClientFunc.apply( serverURI );
		if (path.endsWith(GRPC))
			return grpcClientFunc.apply( serverURI );		
		throw new RuntimeException("Unknown service type..." + serverURI);	
	}
			
}
