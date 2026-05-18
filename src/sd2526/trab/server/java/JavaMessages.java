package sd2526.trab.server.java;

import sd2526.trab.api.InboxEntry;
import sd2526.trab.api.Message;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Users;
import sd2526.trab.clients.java.Clients;
import sd2526.trab.clients.rest.RestMessagesClient;
import sd2526.trab.clients.rest.RestUsersClient;
import sd2526.trab.discovery.Discovery;
import sd2526.trab.server.persistence.Hibernate;
import sd2526.trab.api.java.Result.ErrorCode;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;


public class JavaMessages implements Messages {

    private static Logger LOG = Logger.getLogger(JavaUsers.class.getName());
    private Hibernate hibernate;
    private String serverDomain;

    private static final Map<String, ExecutorService> domainExecutors = new ConcurrentHashMap<>();

    // These store keys for operations that arrived BEFORE the message itself was forwarded here.
    // Tombstone for removeInboxMessage: stores "messageId.username"
    // so that when the forwarded postMessage eventually arrives, it skips creating the InboxEntry
    private final Set<String> pendingRemoves = Collections.synchronizedSet(new HashSet<>());

    // Tombstone for deleteMessage: stores messageIds
    // so that when the forwarded postMessage eventually arrives, it skips persisting the message entirely
    private final Set<String> pendingDeletes = Collections.synchronizedSet(new HashSet<>());

    public JavaMessages(String serverDomain) {
        this.hibernate = Hibernate.getInstance();
        this.serverDomain = serverDomain;
    }

    private String changeToUserName(String sender) {
        String senderName;
        if (sender.contains("<")) {
            sender = sender.substring(sender.indexOf("<") + 1);
        }

        if (sender.contains("@")) {
            senderName = sender.substring(0, sender.indexOf("@"));
        } else {
            senderName = sender;
        }

        return senderName;
    }


    /**
     * Because the User and Messages are different servers, the Message server has to ask the User server
     * if, for example, the pwd is correct for some respective username
     * The only way to do this is via HTTP, and RestUsersClient is a way of talking to the User server over HTTP
     */
    private Result<User> authenticateUser(String username, String pwd) throws IOException {

        // uses the factory to dynamically get a REST or gRPC client for the target domain
        Users client = Clients.UsersClient.get(serverDomain);

        //checks that the password is correct for the username
        Result<User> result = client.getUser(username, pwd);

        if (!result.isOK()) {
            return Result.error(ErrorCode.FORBIDDEN);
        }

        return result;
    }

    private ExecutorService getDomainExecutor(String domain) {
            /*ExecutorService here is used to create a permanent threads for each remote domain.
            Each thread executes tasks given, in order, that are put in a queue.
            If the thread has no tasks to execute at a given moment, it just sits idle waiting for a new task in the queue

            Each task for the executor thread is the code required to forward a message to a specific domain, from this message server.
             */
        return domainExecutors.computeIfAbsent(
                domain, d -> Executors.newSingleThreadExecutor()
        );
            /* computeIfAbsent tries to get the value with the domain provided
             if it already existed, it simply returns it
             if it did not exist yet, it is computed by the lambda d -> Executors.newSingleThreadExecutor(), then stored
             and then returned
            */
    }

    // private method used to create a notification of a failure (TIMEOUT or UNKNOWN USER)
    private Message sendNotification(Message msg, String destination, String senderName, String senderDomain, String reason) {
        Message notification = new Message();
        notification.setId(msg.getId() + "." + destination);
        notification.setSender(msg.getSender());
        notification.setCreationTime(System.currentTimeMillis());
        notification.setDestination(Set.of(senderName + "@" + senderDomain));
        notification.setSubject("FAILED TO SEND " + msg.getId() + " TO " + destination + reason);
        notification.setContents(msg.getContents());

        return notification;
    }

    private void deliverNotification(Message notification, String senderName, String senderDomain, String pwd) {
        hibernate.persist(notification);

        if (senderDomain.equals(serverDomain)) {
            InboxEntry inboxEntry = new InboxEntry(notification.getId(), notification.getSender(), senderName);
            hibernate.persist(inboxEntry);
        } else {
            //send back to failure notification to the server that originated the message
            //create a new thread to avoid blocking this thread and let it finish the processing postMessage request
            Thread handler = new Thread(() -> {
                try {
                    Messages remoteClient = Clients.MessagesClient.get(senderDomain);
                    Result<String> forwarded = remoteClient.postMessage(pwd, notification);

                    if (!forwarded.isOK()) {
                        LOG.info("Failed to forward message to " + senderDomain);
                    }

                } catch (Exception e) {
                    LOG.info("failed to notify sender server of domain " + senderDomain + " message failure");
                }
            });
            handler.setDaemon(true);
            handler.start();// actually starts the new thread
        }
    }

    /**
     * Auxiliary method to postMessage(String pwd, Message msg)
     */
    private void for_each_destination(String senderName, String senderDomain, String pwd, Message msg) throws IOException {
        // one remote client in this map per remote domain to forward one or more messages
        // this is to avoid forwarding messages once per destination in remote domains instead of once per remote domains
        Set<String> remoteDomains = new HashSet<>();

        for (String destination : msg.getDestination()) {
            String domain = getDomain(destination);
            if (!domain.equals(serverDomain)) {
                if (senderDomain.equals(serverDomain)) {
                    /* this second condition is to avoid infinite forwarding:
                        say domain A sends a message to users in domain A and B,
                        when the message reaches domain B, domain B will then try to forward the message back to domain A,
                        and this will continue forever
                    */
                    if (!remoteDomains.contains(domain)) {
                        remoteDomains.add(domain);

                        getDomainExecutor(domain).submit(() -> {// this submits a task to the thread for this specific domain. The thread for each domain never dies between tasks
                            boolean sent = false;
                            long startTime = System.currentTimeMillis();
                            long timeout = 90000; // 90s

                            while (!sent) {
                                long elapsed = System.currentTimeMillis() - startTime;

                                // If we passed the 90s timer
                                if (elapsed >= timeout) {
                                    // Generate the failure notification and send it; after that stop the loop
                                    Message notification = sendNotification(msg, destination, senderName, senderDomain, ": TIMEOUT");
                                    deliverNotification(notification, senderName, senderDomain, pwd);
                                    break;
                                }

                                try {
                                    // calculate the exact time we have left before 90s
                                    long remaining = timeout - elapsed;

                                    // HTTP network calls can be dangerous because they can block forever
                                    // CompletableFuture provides a way to write asynchronous code by representing a future result that will eventually appear
                                    // onto a completely separate, temporary background thread
                                    CompletableFuture<Result<String>> future = CompletableFuture.supplyAsync(() ->
                                    {
                                        try {
                                            //discovery is used inside the method get, and the endpoint itself defines if this is done with REST or gRPC
                                            return Clients.MessagesClient.get(domain).postMessage(pwd, msg);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });

                                    // the current thread waits for the background thread to finish
                                    // the parameter 'remaining' gives a strict limit, if it takes one second longer, throws a TimeoutException
                                    Result<String> forwarded = future.get(remaining, TimeUnit.MILLISECONDS); // the future only starts executing here

                                    if (forwarded.isOK()) {
                                        sent = true;
                                    } else {
                                        Thread.sleep(1000); // retry timeout
                                    }

                                } catch (TimeoutException e) {
                                    // future.get hit the 90s limit, so we create a new timeout notification and stop the loop
                                    Message notification = sendNotification(msg, destination, senderName, senderDomain, ": TIMEOUT");
                                    deliverNotification(notification, senderName, senderDomain, pwd);
                                    break;

                                } catch (Exception e) {
                                    try {
                                        Thread.sleep(1000); // wait 1 sec before trying to reach out to the server again
                                    } catch (InterruptedException e1) {
                                        break;
                                    }
                                }
                            }
                        });
                    }
                }
                //to avoid creating a local inbox entry for a message belonging to a server from another domain
                continue;
            }

            String destinationName = changeToUserName(destination);

            boolean exists = false;
            if (serverDomain.equals(senderDomain)) {
                Result<List<User>> searchResult = Clients.UsersClient.get(domain).searchUsers(senderName, pwd, destinationName);

                if (searchResult.isOK() && searchResult.value() != null) {
                    for (User u : searchResult.value()) {
                        if (u.getName().equals(destinationName)) {
                            exists = true;
                            break;
                        }
                    }
                }
            } else {// the message was forwarded to this MessagesServer from another domain
                // if its in other domain, we assume that the destination exists, because we can't authenticate our user server with the credentials
                // of the remote sender
                Result<Void> result = Clients.UsersClient.get(domain).userExists(destinationName);

                exists = (result.error() != ErrorCode.NOT_FOUND);
            }

            if (!exists) {
                Message notification = sendNotification(msg, destination, senderName, senderDomain, ": UNKNOWN USER");
                deliverNotification(notification, senderName, senderDomain, pwd);
                continue;
            }

            // check if a removeInboxMessage tombstone exists for this message+user combination.
            // This handles the race condition where removeInboxMessage arrived before postMessage.
            String key = msg.getId() + "." + destinationName;
            if (pendingRemoves.remove(key)) {
                // Tombstone found: the client already asked to remove this message from this inbox.
                // skip creating the InboxEntry — the message was effectively never delivered.
                LOG.info("Skipping InboxEntry for " + destinationName + " - already removed (tombstone)");
            } else {
                // Normal case: no tombstone, create the InboxEntry as usual
                InboxEntry entry = new InboxEntry(msg.getId(), senderName, destinationName);
                hibernate.persist(entry);
            }
        }
    }


    /**
     * Posts a new message to the server, associating it to the inbox of each individual destination.
     * An outgoing message should be modified before delivering it, by assigning it an ID, and by
     * changing the sender to be in the format "display name <name@domain>".
     * Posting exactly the same message more than once, should have no effect but still succeed by
     * returning its ID.
     * <p>
     * NOTE: there might be some destinations that are not from the local domain (see grading for
     * how addressing this feature is valued).
     *
     * @param msg the message object to be posted to the server
     * @param pwd password of the user posting the message
     * @return (OK, the unique identifier for the posted message)
     * FORBIDDEN - if the sender does not exist, or, if the pwd is not correct (NOTE: sender can be in the form "name" or "name@domain");
     * BAD_REQUEST if the parameters are invalid (eg., null parameters, or a malformed message).
     */
    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        LOG.info("Posting message: " + msg);

        if (pwd == null || msg == null) {
            LOG.info("Invalid Message");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        String sender = msg.getSender();
        if (sender == null) {
            LOG.info("Invalid Sender");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        String senderName = changeToUserName(sender);
        String senderFormat;

        try {
            String senderDomain = getDomain(sender);

            if (senderDomain.equals(serverDomain)) {
                Result<User> auth = authenticateUser(senderName, pwd);
                if (!auth.isOK()) {
                    LOG.info("Authentication failed");
                    return Result.error(auth.error());
                }

                User user = auth.value();
                if (user == null || !user.getPwd().equals(pwd)) {
                    LOG.info("User not found.");
                    return Result.error(ErrorCode.FORBIDDEN);
                }

                // changing the sender to be in the format "display name <name@domain>"
                senderFormat = user.getDisplayName() + " <" + senderName + "@" + user.getDomain() + ">";
                msg.setSender(senderFormat);
            } else {
                // the sender is from another domain and the message is being sent from that other domain to this server
                // the origin server must have already authenticated the sender in their server, so we should not try to do it again in our domain
                senderFormat = msg.getSender();
            }

            // See if the message doesn't exist in the database, if we don't have the ID to search for the message,
            // we use the query to search for a message that as the same sender and was created the same time as the msg
            String query = "SELECT m FROM Message m WHERE m.sender = '" + senderFormat + "' AND m.creationTime = " + msg.getCreationTime();
            List<Message> message = hibernate.jpql(query, Message.class);

            if (!message.isEmpty()) {
                LOG.info("Message already exists.");
                return Result.ok(message.get(0).getId());
            }

            if (msg.getId() == null) {
                // only set the id if it did not already exist, to not override an id from the original server when forwarding to another domain
                msg.setId(UUID.randomUUID().toString());// generates a random string for the ID
            }

            for_each_destination(senderName, senderDomain, pwd, msg);

            hibernate.persist(msg);

            // Before processing this message, check if a deleteMessage tombstone exists for it.
            // This handles the race condition where deleteMessage arrived before the forwarded postMessage.
            if (pendingDeletes.remove(msg.getId())) {
                // Tombstone found: the client already asked to delete this message.
                // Skip persisting it and creating any InboxEntries — act as if it was never posted.
                LOG.info("Message already been deleted. (tombstone)");
                return Result.ok();
            }

            return Result.ok(msg.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Gets the domain of the destination
     *
     * @param email
     * @return the domain of the email
     */
    private String getDomain(String email) {
        String domain = email.substring(email.indexOf("@") + 1);

        if (domain.contains(">")) {
            domain = domain.substring(0, domain.indexOf(">"));// to strip the ">" in the sender format
        }
        return domain;
    }

    /**
     * Obtains a single message from the inbox of a user.
     *
     * @param name the owner of the inbox
     * @param mid  the identifier of the message to be retrieved
     * @param pwd  password of the owner of the inbox
     * @return (OK, the message if it exists);
     * FORBIDDEN - if the name does not exist or if the password is not correct;
     * NOT_FOUND - if the message does not exist;
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        if (name == null || name.isBlank() || mid == null || mid.isBlank() || pwd == null) {
            LOG.info("Invalid Message");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        String username = changeToUserName(name);

        try {
            Result<User> auth = authenticateUser(username, pwd);
            if (!auth.isOK()) {
                LOG.info("Authentication failed");
                return Result.error(auth.error());
            }

            User user = auth.value();
            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            // checks if the user as the message in their inbox
            // safety check
            String query = "SELECT i FROM InboxEntry i WHERE i.destination = '" + username + "' AND i.messageId = '" + mid + "'";
            List<InboxEntry> entries = hibernate.jpql(query, InboxEntry.class);
            if (entries.isEmpty()) {
                return Result.error(ErrorCode.NOT_FOUND);
            }

            Message msg = hibernate.get(Message.class, mid);
            if (msg == null) {
                return Result.error(ErrorCode.NOT_FOUND);
            }

            return Result.ok(msg);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Returns all the messages from the inbox of a user.
     *
     * @param name the owner of the inbox
     * @param pwd  password of the owner of the inbox
     * @return (OK, list of ids, potentially empty);
     * FORBIDDEN if the name does not exist or if the password is not correct.
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        if (name == null || name.isBlank() || pwd == null) {
            LOG.info("Invalid Message");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        String username = changeToUserName(name);

        try {
            Result<User> auth = authenticateUser(username, pwd);
            if (!auth.isOK()) {
                LOG.info("Authentication failed");
                return Result.error(auth.error());
            }

            User user = auth.value();
            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            // checks if the user as the message in their inbox
            // safety check
            String query = "SELECT i FROM InboxEntry i WHERE i.destination = '" + username + "'";
            List<InboxEntry> entries = hibernate.jpql(query, InboxEntry.class);

            List<String> ids = new ArrayList<>();
            for (InboxEntry entry : entries) {
                ids.add(entry.getMessageId());
            }

            return Result.ok(ids);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Removes a single message from the inbox of a user.
     *
     * @param name the owner of the inbox
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the owner of the inbox
     * @return NO_CONTENT if ok
     * FORBIDDEN if the name does not exist or if the password is not correct.
     * NOT_FOUND - if the message does not exist;
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        if (name == null || name.isBlank() || mid == null || mid.isBlank() || pwd == null) {
            LOG.info("Invalid Message");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        String username = changeToUserName(name);

        try {
            Result<User> auth = authenticateUser(username, pwd);
            if (!auth.isOK()) {
                LOG.info("Authentication failed");
                return Result.error(auth.error());
            }

            User user = auth.value();
            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            Message msg = hibernate.get(Message.class, mid);
            if (msg == null) {
                LOG.info("Message not found.");
                return Result.error(ErrorCode.NOT_FOUND);
            }

            String query = "SELECT i FROM InboxEntry i WHERE i.destination = '" + username + "' AND i.messageId = '" + mid + "'";
            List<InboxEntry> entries = hibernate.jpql(query, InboxEntry.class);
            if (entries.isEmpty()) {
                // Inbox entry missing: message either already removed or user wasn't a recipient.
                // Return NOT_FOUND (legitimate error).
                Message m = hibernate.get(Message.class, mid);
                if (m != null) {
                    return Result.error(ErrorCode.NOT_FOUND);
                }

                // Message hasn't arrived yet (cross-domain race condition).
                // Store a tombstone to skip InboxEntry creation upon arrival, and return OK.
                pendingRemoves.add(mid + "." + username);
                return Result.ok();
            }
            // Normal case: inbox entry exists, delete it
            hibernate.delete(entries.get(0));

            return Result.ok();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

    }

    /**
     * Deletes a message from all inboxes and servers where it is stored. A message should only be deleted if it was
     * posted less than 30 seconds ago.
     * The deletion can be executed asynchronously and does not generate an error message if the
     * message does not exist.
     *
     * @param name the sender of the message to be deleted
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the sender
     * @return NO_CONTENT if ok
     * FORBIDDEN if the sender does not exist or if the password is not correct;
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public synchronized Result<Void> deleteMessage(String name, String mid, String pwd) {
        if (name == null || name.isBlank() || mid == null || mid.isBlank() || pwd == null) {
            LOG.info("Invalid Message");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        String username = changeToUserName(name);
        try {
            Message msg = hibernate.get(Message.class, mid);
            if (msg == null) {
                // Message not found, either it was already deleted, or this is a race condition.
                // If a race condition, store a tombstone to block future persistence of this message.
                pendingDeletes.add(mid);
                LOG.info("Message doesn't exist.");
                return Result.ok();
            }

            String senderDomain = getDomain(msg.getSender());

            // we only try to authenticate in this server if this server has the person who created and is trying to delete the msg
            if (senderDomain.equals(serverDomain)) {
                Result<User> auth = authenticateUser(username, pwd);
                if (!auth.isOK()) {
                    LOG.info("Authentication failed");
                    return Result.error(auth.error());
                }

                User user = auth.value();
                if (user == null || !user.getPwd().equals(pwd)) {
                    LOG.info("User not found.");
                    return Result.error(ErrorCode.FORBIDDEN);
                }
            }

            // check if the username requesting the deletion is the sender
            // added because I was getting an error in the test 4c)
            if (!msg.getSender().contains(username)) {
                LOG.info("The user name is not the sender.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            // if is posted in bigger then 30s, we can't deleted
            long diff = System.currentTimeMillis() - msg.getCreationTime();

            // we only check time if the sender is in the same domain as the server
            if (diff > 30000 && senderDomain.equals(serverDomain)) {
                LOG.info("Can't delete message because is posted more than 30 seconds.");
                return Result.ok();
            }

            // else, we delete the message in the InboxEntry and from the Message database
            String query = "SELECT i FROM InboxEntry i WHERE i.messageId =  '" + mid + "'";
            List<InboxEntry> entries = hibernate.jpql(query, InboxEntry.class);

            for (InboxEntry entry : entries) {
                hibernate.delete(entry);
            }
            hibernate.delete(msg);

            if (senderDomain.equals(serverDomain)) {// to avoid an infinite loop like in postMessage to other domains
                Set<String> remoteDomains = new HashSet<>();//we use a set to avoid deleting the same message twice
                for (String destination : msg.getDestination()) {
                    String domain = getDomain(destination);

                    if (!domain.equals(serverDomain) && remoteDomains.add(domain)) {
                        getDomainExecutor(domain).submit(() -> {
                            try {
                                boolean deleted = false;
                                while (!deleted) {
                                    try {
                                        Result<Void> result = Clients.MessagesClient.get(domain).deleteMessage(name, mid, pwd);

                                        if (result.isOK() || result.error() == ErrorCode.NOT_FOUND) {
                                            deleted = true;
                                        } else {
                                            Thread.sleep(1000);
                                        }
                                    } catch (Exception e) {
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException ie) {
                                            LOG.info("failed to delete message on remote domain " + domain);
                                            break;
                                        }
                                    }
                                }

                            } catch (Exception e) {
                                LOG.info("failed to delete message on remote domain " + domain);
                            }
                        });
                    }
                }
            }

            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Returns the list of identifiers of the messages in the inbox of a user that match a query. A message matches
     * when the query is a substring of the subject or contents, case-insensitive.
     *
     * @param name  the owner of the inbox
     * @param pwd   password of the owner of the inbox
     * @param query - substring to search
     * @return (OK, list of hits, may be empty.) .
     * FORBIDDEN if the name does not exist or if the password is not correct.
     * BAD_REQUEST if the parameters are invalid (eg., null parameters).
     */
    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        if (name == null || name.isBlank() || pwd == null) {
            LOG.info("Invalid Message");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        String username = changeToUserName(name);
        try {
            Result<User> auth = authenticateUser(username, pwd);
            if (!auth.isOK()) {
                LOG.info("Authentication failed");
                return Result.error(auth.error());
            }

            User user = auth.value();
            if (user == null || !user.getPwd().equals(pwd)) {
                LOG.info("User not found.");
                return Result.error(ErrorCode.FORBIDDEN);
            }

            // grab the user inboxes
            String inboxQuery = "SELECT i FROM InboxEntry i WHERE i.destination = '" + username + "'";
            List<InboxEntry> entries = hibernate.jpql(inboxQuery, InboxEntry.class);

            List<String> ids = new ArrayList<>();
            for (InboxEntry entry : entries) {
                Message msg = hibernate.get(Message.class, entry.getMessageId());

                // if the query exist in the subject or in the content of the message, we save the msg id in the list
                if (msg.getSubject().toLowerCase().contains(query.toLowerCase()) ||
                        msg.getContents().toLowerCase().contains(query.toLowerCase())) {
                    ids.add(entry.getMessageId());
                }
            }

            return Result.ok(ids);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }
}
