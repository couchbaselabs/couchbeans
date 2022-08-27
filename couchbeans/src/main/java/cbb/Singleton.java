package cbb;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Singleton {
    private String source;

    private transient Object instance;

    private final static Map<String, Singleton> INSTANCES = Collections.synchronizedMap(new HashMap<>());
    private transient String beanType;

    public Singleton() {

    }

    public Singleton(String beanType) {
        this.beanType = beanType;
        INSTANCES.put(beanType, this);
    }

    public String naturalKey() {
        return beanType == null ? Couchbeans.key(this) : beanType;
    }

    public Object get() {
        if (instance == null) {
            try {
                if (source == null) {
                    instance = Couchbeans.create(beanType);
                    source = Utils.MAPPER.writeValueAsString(instance);
                } else {
                    instance = Utils.MAPPER.readValue(source, Couchbeans.getBeanType(beanType).orElseThrow());
                }
            } catch (JsonProcessingException e) {
                BeanException.reportAndThrow(this, e);
            }
        }
        return instance;
    }

    public static Singleton get(String forType) {
        if (!INSTANCES.containsKey(forType)) {
            INSTANCES.put(forType, Couchbeans.get(Singleton.class, forType).orElseThrow());
        }
        return INSTANCES.get(forType);
    }

    public Object update(String source) throws JsonProcessingException {
        Object bean = get();
        Utils.updateBean(bean, this.source, source);
        this.source = source;
        return bean;
    }
}
