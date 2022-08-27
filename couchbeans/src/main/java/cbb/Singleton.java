package cbb;

import com.couchbase.client.java.json.JsonObject;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Singleton {
    private String source;

    private transient Object instance;

    private final static Map<String, Singleton> INSTANCES = Collections.synchronizedMap(new HashMap<>());
    private String beanType;

    public Singleton() {

    }

    public Singleton(String beanType) {
        this.beanType = beanType;
        this.source = "{}";
        INSTANCES.put(beanType, this);
    }

    public static Singleton get(Object bean) {
        return get(bean.getClass());
    }

    public static Singleton get(Class forType) {
        return get(forType.getCanonicalName());
    }

    public String naturalKey() {
        return beanType == null ? Couchbeans.key(this) : beanType;
    }

    public Object get() {
        if (instance == null) {
            instance = Couchbeans.create(beanType);
            Utils.updateBean(instance, "{}", source,true);
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
        JsonObject parsed = JsonObject.fromJson(source);
        Object bean = get();
        Utils.updateBean(bean, this.source, parsed.getString(source), true);
        this.source = source;
        return bean;
    }
    public static void initializeNode() {
        Utils.getAllSingletons().forEach(singleton -> {
            System.out.println(String.format("Initializing singleton: %s", singleton.getClass().getCanonicalName()));
            singleton.get();
            INSTANCES.put(singleton.beanType(), singleton);
        });
    }

    private String beanType() {
        return beanType;
    }

    public String getSource() {
        return source;
    }
}
