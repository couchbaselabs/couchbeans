public class UserPage {
    private String text;

    public void updateParent(ChatServer server, User user) {
        text = "Hello, " + user.username();
    }
}
