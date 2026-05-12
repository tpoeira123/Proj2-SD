package sd2526.trab.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.Map;


/**
 * <p>
 * A class to perform service discovery, based on periodic service contact
 * endpoint announcements over multicast communication.
 * </p>
 *
 * <p>
 * Servers announce their *name* and contact *uri* at regular intervals. The
 * server actively collects received announcements.
 * </p>
 *
 * <p>
 * Service announcements have the following format:
 * </p>
 *
 * <p>
 * &lt;service-name-string&gt;&lt;delimiter-char&gt;&lt;service-uri-string&gt;
 * </p>
 */
public class Discovery {
    private static Logger LOG = Logger.getLogger(Discovery.class.getName());

    static final int DISCOVERY_RETRY_TIMEOUT = 5000;

    static {
        // addresses some multicast issues on some TCP/IP stacks
        System.setProperty("java.net.preferIPv4Stack", "true");
        // summarizes the logging format
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    // Ensures we only create one Discovery
    private static Discovery instance;

    // The pre-agreed multicast endpoint assigned to perform discovery.
    // Allowed IP Multicast range: 224.0.0.1 - 239.255.255.255
    static final public InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
    static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;
    static final int MAX_DATAGRAM_SIZE = 65536;

    // Used separate the two fields that make up a service announcement.
    private static final String DELIMITER = "\t";

    private final MulticastSocket ms;

    // Maps all servers. For every server is attached a timestamp
    // ConcurrentHashMap for thread safety
    private final Map<String, Map<URI, Long>> knownServers = new ConcurrentHashMap<>();

    /**
     * Sets the multicast socket and links it to the localHost
     * @throws IOException
     */
    private Discovery() throws IOException {
        this.ms = new MulticastSocket(DISCOVERY_ADDR.getPort());
        this.ms.joinGroup(DISCOVERY_ADDR, NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
    }

    /**
     * Gets the single shared instance of the Discovery
     * Synchronized ensures that two threads don't create two Discoveries
     */
    public static synchronized Discovery getInstance() throws IOException {
        if (instance == null) {
            instance = new Discovery();
        }
        return instance;
    }

    /**
     * Boots up the two threads: one announce and the other listens
     */
    public void start(String serviceName, String serviceURI) {
        announcerThread(serviceName, serviceURI);//runs in case this is called from a server
        listenerThread();
    }

    /**
     * If this server has a name and URI, start the thread that broadcasts it to the network
     */
    private void announcerThread(String serviceName, String serviceURI) {
        if (serviceName != null && serviceURI != null) {
            LOG.info(String.format("Starting Discovery announcements for: %s -> %s", serviceName, serviceURI));

            // formats the message
            byte[] announceBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
            DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, DISCOVERY_ADDR);

            try {
                // start thread to send periodic announcements
                Thread announcer = new Thread(() -> {
                    for (; ; ) {
                        try {
                            ms.send(announcePkt);   // sends the packet
                            Thread.sleep(DISCOVERY_ANNOUNCE_PERIOD);    // waits 1s
                        } catch (Exception e) {
                            e.printStackTrace();
                            // do nothing
                        }
                    }
                });
                // setDaemon: if the main server stops, kill the thread
                announcer.setDaemon(true);
                announcer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Listens and writes down everything it hears (from the network) to the Map
     */
    private void listenerThread() {
        Thread listener = new Thread(() -> {
            DatagramPacket pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
            for (; ; ) {
                try {
                    pkt.setLength(MAX_DATAGRAM_SIZE);
                    ms.receive(pkt);    // receive the packet

                    // convert the bytes into String
                    String msg = new String(pkt.getData(), 0, pkt.getLength());
                    String[] msgElems = msg.split(DELIMITER);

                    if (msgElems.length == 2) {
                        // Extract the servername and the URI.
                        String server_name = msgElems[0];
                        URI uri = URI.create(msgElems[1]);

                        // finds the dictionary for the Service Name (or create in case it new)
                        // then insert or updates the URI and stamp it with the exact current time
                        knownServers.computeIfAbsent(server_name, k -> new ConcurrentHashMap<>()).put(uri, System.currentTimeMillis());
                    }
                } catch (IOException e) {
                    // do nothing
                }
            }
        });
        // setDaemon: if the main server stops, kill the thread
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Looks up a service and returns its URL
     * Waits if the service hasn't been discovered yet
     *
     * @param serviceName the name of the service being discovered
     * @return an array of URI with the service instances discovered.
     *
     */
    public String knownUrisOf(String serviceName) {
        while (true) {
            // get the known URIs for the requested service name
            Map<URI, Long> servers = knownServers.get(serviceName);

            // if the map exists, grab the first one and return it
            if (servers != null && !servers.isEmpty()) {
                return servers.keySet().iterator().next().toString();
            }

            long now = System.currentTimeMillis();

            // Apaga os URIs que já foram anunciados à mais de DISCOVERY_RETRY_TIMEOUT ms
            servers.entrySet().removeIf(e -> now - e.getValue() > DISCOVERY_RETRY_TIMEOUT);

            // if the service hasn't been found, wait 1s and check again
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
