import couchbeans.annotations.Index;

import java.util.List;

public class User {
    @Index
    private String username;
    @Index
    private String email;
    private List<String> rooms;

    private byte age;

    public String username() {
        return username;
    }

    public void linkTo(ChatRoom room) {
        this.rooms.add(room.name());
    }

    public void unlinkFrom(ChatRoom room) {
        this.rooms.remove(room.name());
    }

    public void age(byte age) {
        this.age = age;
    }
}
