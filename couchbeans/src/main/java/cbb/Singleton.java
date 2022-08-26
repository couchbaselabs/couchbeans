package cbb;

public class Singleton {
    private Object object;

    public Singleton() {

    }

    public Singleton(Object object) {
        this.object = object;
    }

    public String naturalKey() {
        return object.getClass().getCanonicalName();
    }
}
