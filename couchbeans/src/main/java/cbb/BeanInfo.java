package cbb;

import com.couchbase.client.java.json.JsonObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Streams;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds information about a bean
 * Used to detect new beans and to deliver field-level changes to the bean
 */
public class BeanInfo {
    private long revision;
    private String beanKey;
    private String beanType;
    private String lastAppliedSource;

    public BeanInfo() {

    }

    protected BeanInfo(String beanType, String beanKey, String source) {
        if (beanType.equals(BeanInfo.class.getCanonicalName())) {
            throw new IllegalArgumentException("Cannot create BeanInfo for BeanInfo...");
        }
        this.beanType = beanType;
        this.beanKey = beanKey;
        this.revision = 0;
        this.lastAppliedSource = source;
    }

    public long revision() {
        return revision;
    }

    public void incrementRevision() {
        revision++;
    }
    protected void setLastAppliedSource(String source) {
        lastAppliedSource = source;
    }

    public String lastAppliedSource() {
        return lastAppliedSource;
    }

    public List<String> detectChangedFields(String newSource) {
        Map<String, Object> oldJson = (lastAppliedSource == null) ? new HashMap<>() : JsonObject.fromJson(lastAppliedSource).toMap();
        Map<String, Object> newJson = JsonObject.fromJson(newSource).toMap();
        return Streams.concat(oldJson.keySet().stream(), newJson.keySet().stream())
                .distinct()
                .filter(key -> !Objects.equals(oldJson.get(key), newJson.get(key)))
                .collect(Collectors.toList());
    }

    public Class beanType() {
        try {
            return Class.forName(beanType);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public Object bean() {
        try {
            Object bean = Utils.MAPPER.readValue(lastAppliedSource, beanType());
            Couchbeans.KEY.put(bean, beanKey);
            return bean;
        } catch (JsonMappingException e) {
            return null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public Object update(String source) throws JsonProcessingException {
        Class type = beanType();
        if (revision == 0 || lastAppliedSource.equals(source)) {
            lastAppliedSource = source;
            return Utils.MAPPER.readValue(source, type);
        } else {
            Object result = Utils.MAPPER.readValue(lastAppliedSource, type);
            Utils.updateBean(result, lastAppliedSource, source, true);
            Couchbeans.KEY.put(result, beanKey);
            return result;
        }
    }
}
