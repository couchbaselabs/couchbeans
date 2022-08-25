import java.util.Date;

public class Message {
    private String sendername;
    private String text;

    private long sent;

    public Message() {

    }

    public Message(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public void linkTo(User user) {
        sendername = user.username();
    }

    public void linkTo(ChatRoom room) {
        this.sent = System.currentTimeMillis();
    }
}
