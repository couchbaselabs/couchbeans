package cbb;

public class Resource {
    private String name = "";
    private byte[] value = new byte[0];

    public Resource() {

    }

    public Resource(String name, byte[] value) {
        this.name = name;
        this.value = value;
    }

    public void name(String name) {
        this.name = name;
    }

    public void value(byte[] value) {
        this.value = value;
    }

    public String name() {
        return name;
    }

    public byte[] value() {
        return value;
    }
}
