package cbb;

public enum BeanScope {
    NORMAL, LOCAL, INTERNAL, EXTERNAL(true), GLOBAL(true), GLOBAL_PREFER_EXTERNAL(true);

    private final boolean isAutoCreated;
    BeanScope() {
        isAutoCreated = false;
    }
    BeanScope(boolean isAutoCreated) {
        this.isAutoCreated = isAutoCreated;
    }

    public boolean isAutoCreated() {
        return isAutoCreated;
    }

}
