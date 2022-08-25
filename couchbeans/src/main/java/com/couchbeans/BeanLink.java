package com.couchbeans;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class BeanLink {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeanLink.class);
    private final String sourceType;
    private final String sourceKey;
    private final String targetType;
    private final String targetKey;

    public BeanLink(Object source, Object target) {
        this.sourceType = source.getClass().getCanonicalName();
        this.sourceKey = Couchbeans.key(source);
        this.targetType = target.getClass().getCanonicalName();
        this.targetKey = Couchbeans.key(target);
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

    public Optional<?> target() {
        try {
            return Couchbeans.get(Class.forName(targetType), targetKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load bean '" + targetKey + "' of type '" + targetType + "'", e);
            BeanException.report(this, e);
            return Optional.empty();
        }
    }

    public Optional<?> source() {
        try {
            return Couchbeans.get(Class.forName(sourceType), sourceKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load bean '" + sourceKey + "' of type '" + sourceType + "'", e);
            BeanException.report(this, e);
            return Optional.empty();
        }
    }
}
