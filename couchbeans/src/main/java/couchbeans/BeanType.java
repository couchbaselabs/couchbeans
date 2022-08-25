package couchbeans;

public enum BeanType {
    NORMAL, LOCAL, INTERNAL, EXTERNAL(true), GLOBAL(true);

    private final boolean isAutoCreated;

    BeanType() {
        isAutoCreated = false;
    }

    BeanType(boolean isAutoCreated) {
        this.isAutoCreated = isAutoCreated;
    }

    public boolean isAutoCreated() {
        return isAutoCreated;
    }
}
