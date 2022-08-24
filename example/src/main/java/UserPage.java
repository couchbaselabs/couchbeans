public class UserPage {
    private String text;

    public UserPage(User user) {
        update(user);
    }

    public void update(User user) {
        text = "Hello, " + user.username();
    }
}
