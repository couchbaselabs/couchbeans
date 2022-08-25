import com.couchbeans.Couchbeans;

import java.util.List;

public class ChatRoom {
    private String name;
    private List<String> usernames;

    public Message linkChild(User user) {
        usernames.add(user.username());
        return new Message(String.format("User _%s_ enters the room.", user.username()));
    }

    public Message unlinkChild(User user) {
        usernames.remove(user.username());
        return new Message(String.format("User _%s_ left the room.", user.username()));
    }

    public String name() {
        return name;
    }
}