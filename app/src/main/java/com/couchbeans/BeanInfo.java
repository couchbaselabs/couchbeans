package com.couchbeans;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class BeanInfo {
    private long revision;
    private Set<String> parentBeans;

    public BeanInfo() {

    }

    protected BeanInfo(Object bean) {
        this.revision = 0;
    }

    public long revision() {
        return revision;
    }

    public void incrementRevision() {
        revision++;
    }
}
