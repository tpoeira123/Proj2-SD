package sd2526.trab.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class InboxEntry {
    @Id
    private String id;
    private String messageId;
    private String sender;
    private String destination;

    public InboxEntry() {
    }

    public InboxEntry(String messageId, String sender, String destination) {
        this.id = UUID.randomUUID().toString();
        this.messageId = messageId;
        this.sender = sender;
        this.destination = destination;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }
    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMessageId() {
        return messageId;
    }
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    public String getDestination() {
        return destination;
    }
}
