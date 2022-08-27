package cbb;

import net.rubygrapefruit.platform.memory.Memory;
import org.gradle.cache.ManualEvictionInMemoryCache;
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
        this(
                source.getClass().getCanonicalName(),
                Couchbeans.key(source),
                target.getClass().getCanonicalName(),
                Couchbeans.key(target)
        );
    }

    public BeanLink(String sourceType, String sourceKey, String targetType, String targetKey) {
        this.sourceType = sourceType;
        this.sourceKey = sourceKey;
        this.targetType = targetType;
        this.targetKey = targetKey;
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
            if (BeanScope.forType(targetType) == BeanScope.GLOBAL) {
                return Optional.of((T) Singleton.get(targetType).get());
            }
            return Couchbeans.get(targetType, targetKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load bean '" + targetKey + "' of type '" + targetType + "'", e);
            BeanException.report(this, e);
            return Optional.empty();
        }
    }

    public Optional<S> source() {
        try {
            if (BeanScope.forType(sourceType) == BeanScope.GLOBAL) {
                return Optional.of((S) Singleton.get(sourceType).get());
            }
            return Couchbeans.get((Class<S>) Class.forName(sourceType), sourceKey);
        } catch (Exception e) {
            LOGGER.error("Failed to load bean '" + sourceKey + "' of type '" + sourceType + "'", e);
            BeanException.report(this, e);
            return Optional.empty();
        }
    }

    public BeanScope scope() {
        return BeanScope.get(sourceType) == BeanScope.MEMORY || BeanScope.get(targetType) == BeanScope.MEMORY ? BeanScope.MEMORY : BeanScope.BUCKET;
    }

}
