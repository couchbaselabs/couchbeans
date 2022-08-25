package com.couchbeans;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class BeanLink<S, T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeanLink.class);
    private String sourceType;
    private String sourceKey;
    private String targetType;
    private String targetKey;

    public BeanLink() {

    }

    public BeanLink(S source, T target) {
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

    public Optional<T> target() {
        try {
            return Couchbeans.get((Class<T>) Class.forName(targetType), targetKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load bean '" + targetKey + "' of type '" + targetType + "'", e);
            BeanException.report(this, e);
            return Optional.empty();
        }
    }

    public Optional<S> source() {
        try {
            return Couchbeans.get((Class<S>)Class.forName(sourceType), sourceKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load bean '" + sourceKey + "' of type '" + sourceType + "'", e);
            BeanException.report(this, e);
            return Optional.empty();
        }
    }
}
