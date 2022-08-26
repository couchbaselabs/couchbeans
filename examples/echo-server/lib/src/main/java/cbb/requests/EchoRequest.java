package cbb.requests;

public class EchoRequest {
    private String message;
    private String source;

    public EchoRequest() {

    }

    public EchoRequest(String message, String source) {
        this.message = message;
        this.source = source;
    }

    public void setMessage() {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
