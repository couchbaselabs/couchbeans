import com.couchbeans.BeanLink;
import com.couchbeans.Couchbeans;
import com.couchbeans.annotations.External;

import java.net.http.WebSocket;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * External beans are global for all external nodes: each external node will maintain a copy of the bean
 * and each copy will be reacting to DCP changes to this bean.
 */
@External
public class ChatServer {
    private int port;
    private boolean running;
    private List<String> roomNames;
    private final transient Map<String, Collection<WebSocket>> sockets = new HashMap<>();

    public ChatServer() {
    }

    /**
     * This will be called only on external nodes
     * On all other nodes this method will be replaced
     * with a simple setter
     * @param running
     */
    public void setRunning(boolean running) {
        if (running != this.running) {
            if (running) {
                // todo: start the server
            } else {
                // todo: terminate the server
            }
            this.running = running;
        }
    }

    /**
     * Called on external nodes when a link from ChatServer to ChatRoom is created on the graph
     * On all other nodes will create a link that points from this bean to the room
     * @param room
     */
    public void linkFrom(ChatRoom room) {
        roomNames.add(room.name());
    }

    /**
     * Called on external nodes when a link from ChatServer to ChatRoom is removed from the graph
     * @param room
     */
    public void unlinkFrom(ChatRoom room) {
        roomNames.remove(room);
    }

    /**
     * Called on external nodes when a link from ChatRoom (that is linked to this object) to Message
     * is created on the graph
     * @param room
     * @param message
     */
    public void linkFrom(ChatRoom room, Message message) {
        Couchbeans.allParents(message, User.class);
    }
}