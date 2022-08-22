package com.couchbeans;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BeanLink {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeanLink.class);
    private final String sourceType;
    private final String sourceKey;
    private final String targetType;
    private final String targetKey;

    public BeanLink(Object source, Object target) {
        this.sourceType = source.getClass().getCanonicalName();
        this.sourceKey = Couchbeans.KEY.get(source);
        this.targetType = target.getClass().getCanonicalName();
        this.targetKey = Couchbeans.KEY.get(target);

        if (sourceKey == null) {
            throw new RuntimeException("Unknown source bean: " + source);
        }
        if (targetKey == null) {
            throw new RuntimeException("Unknown target bean: " + target);
        }
    }

    public String sourceType() {
        return sourceType;
    }

    public String sourceKey() {
        return sourceKey;
    }

    public String targetType() {
        return targetType;
    }

    public String targetKey() {
        return targetKey;
    }

    public Object target() {
        try {
            return Couchbeans.get(Class.forName(targetType), targetKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load bean '" + targetKey + "' of type '" + targetType + "'", e);
            return null;
        }
    }
}
