package cbb;

import com.couchbase.client.core.cnc.EventBus;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.codec.JacksonJsonSerializer;
import com.couchbase.client.java.codec.JsonSerializer;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;

@cbb.annotations.Scope(BeanScope.GLOBAL)
public class Couchbeans {
    public static final Cluster CLUSTER;
    public static final Bucket BUCKET;
    public static final Scope SCOPE;
    public static final String CBB_BUCKET = Utils.envOrDefault("CBB_BUCKET", "default");
    public static final String CBB_SCOPE = Utils.envOrDefault("CBB_SCOPE", "_default");
    public static final String CBB_CLUSTER = Utils.envOrDefault("CBB_CLUSTER", "couchbase://localhost");
    public static final String CBB_USERNAME = Utils.envOrDefault("CBB_USERNAME", "Administrator");

    public static final EventBus EVENT_BUS;

    public static final JsonSerializer SERIALIZER;
    public static final Node NODE = new Node();

    protected static final Map<Class, Map<String, Object>> LOCAL_BEANS = Collections.synchronizedMap(new HashMap<>());
    protected static final Map<Class, Map<String, Object>> GLOBAL_BEANS = Collections.synchronizedMap(new HashMap<>());
    protected static final Map<Object, Void> OWNED = Collections.synchronizedMap(new WeakHashMap<>());
    protected static final Map<Object, String> KEY = Collections.synchronizedMap(new WeakHashMap<>());

    // membeans :)
    protected static final Map<Class, Map<String, Object>> MEMBEANS = Collections.synchronizedMap(new HashMap<>());

    // index by class name then by bean key then by other bean type and finally by link id
    protected static final Map<String, Map<String, Map<String, Set<String>>>> LINK_INDEX = Collections.synchronizedMap(new HashMap<>());
    protected static final Map<String, Map<String, Map<String, Set<String>>>> REVERSE_LINK_INDEX = Collections.synchronizedMap(new HashMap<>());

    static {
        Utils.MAPPER.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        Utils.MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        CLUSTER = Cluster.connect(
                CBB_CLUSTER,
                ClusterOptions.clusterOptions(CBB_USERNAME, Utils.envOrDefault("CBB_PASSWORD", "password")).environment(ClusterEnvironment.builder().jsonSerializer(
                        JacksonJsonSerializer.create(Utils.MAPPER)
                ).build())
        );
        BUCKET = CLUSTER.bucket(CBB_BUCKET);
        SCOPE = BUCKET.scope(CBB_SCOPE);
        EVENT_BUS = CLUSTER.environment().eventBus();
        SERIALIZER = SCOPE.environment().jsonSerializer();
    }

    public static <T> Optional<T> get(Class<T> type, String id) {
        if (MEMBEANS.containsKey(type) && MEMBEANS.get(type).containsKey(id)) {
            return Optional.of((T) MEMBEANS.get(type).get(id));
        }
        return Utils.fetchObject(type, id);
    }

    public static <T> Optional<T> firstChild(Object bean, Class<T> type) {
        return Utils.children(bean, type)
                .stream()
                .map(BeanLink::target)
                .map(type::cast)
                .findFirst();
    }

    public static <T> Stream<T> allChildren(Object bean, Class<T> type) {
        return Utils.children(bean, type)
                .stream()
                .map(type::cast);
    }

    public static <T> Stream<T> allParents(Object bean, Class<T> type) {
        return Utils.parents(bean, type)
                .stream()
                .map(type::cast);
    }

    public static String key(Object bean) {
        synchronized (KEY) {
            if (!KEY.containsKey(bean)) {
                try {
                    Method keyMethod = bean.getClass().getMethod("naturalKey");
                    String key = (String) keyMethod.invoke(bean);
                    KEY.put(bean, key);
                    return key;
                } catch (InvocationTargetException e) {
                    BeanException.report(bean, e);
                } catch (Exception e) {
                    // noop
                }
                KEY.put(bean, UUID.randomUUID().toString());
            }
        }
        return KEY.get(bean);
    }

    public static String store(Object bean) {
        String key = key(bean);
        Class beanType = bean.getClass();

        if (BeanScope.get(bean) == BeanScope.MEMORY) {
            if (beanType == BeanLink.class) {
                // update the indexes
                BeanLink link = (BeanLink) bean;
                updateLinkIndex(LINK_INDEX, link.sourceType(), link.sourceKey(), link.targetType(), key);
                updateLinkIndex(REVERSE_LINK_INDEX, link.targetType(), link.targetKey(), link.sourceType(), key);
            }
            if (!MEMBEANS.containsKey(beanType)) {
                MEMBEANS.put(beanType, Collections.synchronizedMap(new WeakHashMap<>()));
            }
            MEMBEANS.get(beanType).put(key, bean);
        } else {
            Utils.ensureCollectionExists(bean.getClass());
            if (owned(bean)) {
                try {
                    String beanTypeName = beanType.getCanonicalName();
                    String source = Utils.MAPPER.writeValueAsString(bean);
                    Utils.getBeanInfo(beanTypeName, key).ifPresentOrElse(bi -> {
                                Utils.updateBean(bean, bi.lastAppliedSource(), source, false);
                                bi.setLastAppliedSource(source);
                                store(bi);
                            },
                            () -> {
                                Utils.updateBean(bean, "{}", source, false);
                                store(new BeanInfo(beanTypeName, key, source));
                            }
                    );
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            SCOPE.collection(Utils.collectionName(bean.getClass())).upsert(key, bean);
        }
        return key;
    }

    private static void updateLinkIndex(Map<String, Map<String, Map<String, Set<String>>>> index, String type, String key, String otherType, String linkKey) {
        if (!index.containsKey(type)) {
            index.put(type, Collections.synchronizedMap(new HashMap<>()));
        }
        {
            Map<String, Map<String, Set<String>>> subIndex = index.get(type);
            if (!subIndex.containsKey(key)) {
                subIndex.put(key, Collections.synchronizedMap(new HashMap<>()));
            }
        }
        {
            Map<String, Set<String>> subIndex = index.get(type).get(key);
            if (!subIndex.containsKey(otherType)) {
                subIndex.put(otherType, Collections.synchronizedSet(new HashSet<>()));
            }
            subIndex.get(otherType).add(linkKey);
        }
    }

    public static void delete(Object bean) {
        String key = key(bean);
        Utils.allChildLinks(bean).forEach(Couchbeans::delete);
        Utils.allParentLinks(bean).forEach(Couchbeans::delete);

        if (BeanScope.get(bean) == BeanScope.MEMORY) {
            Class beanType = bean.getClass();
            if (MEMBEANS.containsKey(beanType)) {
                MEMBEANS.get(beanType).remove(key);
            }
        } else {
            SCOPE.collection(Utils.collectionName(bean.getClass())).remove(key(bean));
        }
    }

    public static <S, T> BeanLink<S, T> link(S source, T target) {
        return Utils.linkBetween(source, target).orElseGet(() -> {
            BeanLink<S, T> result = new BeanLink<S, T>(source, target);
            store(result);
            return result;
        });
    }

    public static String ref(Object bean) {
        return String.format("couchbean://%s/%s", bean.getClass().getCanonicalName(), key(bean));
    }

    public static boolean owned(Object bean) {
        return OWNED.containsKey(bean);
    }

    public static <T> Optional<T> firstChild(Class<?> beanType, String key, Class<T> targetType) {
        return Utils.children(beanType, key, targetType)
                .stream()
                .findFirst()
                .map(l -> (T) l.target());
    }

    public static boolean storeIfNotExists(Singleton singleton) {
        String key = Couchbeans.key(singleton);
        if (!exists(Singleton.class, key)) {
            store(singleton);
            return true;
        }
        return false;
    }

    private static boolean exists(Class<?> type, String key) {
        return SCOPE.collection(Utils.collectionName(type)).exists(key).exists();
    }

    public static Optional<BeanLink> getLink(String s) {
        return get(BeanLink.class, s);
    }

    public static Optional<Class> getBeanType(String name) {
        try {
            return Optional.of(Class.forName(name, true, CouchbaseClassLoader.INSTANCE));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> get(String targetType, String targetKey) {
        return get(Couchbeans.getBeanType(targetType).orElseThrow(), targetKey);
    }

    public static Object create(String beanType) {
        try {
            Object bean = getBeanType(beanType).orElseThrow().getConstructor().newInstance();
            key(bean);
            return bean;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
