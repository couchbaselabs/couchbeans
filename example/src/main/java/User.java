import java.util.List;

public class User {
    private String username;
    private String email;
    private List<String> rooms;

    public String username() {
        return username;
    }

    public void linkTo(ChatRoom room) {
        this.rooms.add(room.name());
    }

    public void unlinkFrom(ChatRoom room) {
        this.rooms.remove(room.name());
    }
}
