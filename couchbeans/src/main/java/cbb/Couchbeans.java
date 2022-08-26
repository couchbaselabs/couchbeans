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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class Couchbeans {
    public static final Cluster CLUSTER;
    public static final Bucket BUCKET;
    public static final Scope SCOPE;
    public static final String CBB_BUCKET = Utils.envOrDefault("CBB_BUCKET", "default");
    public static final String CBB_SCOPE = Utils.envOrDefault("CBB_SCOPE", "_default");
    public static final String CBB_CLUSTER = Utils.envOrDefault("CBB_CLUSTER", "couchbase://localhost");
    public static final String CBB_USERNAME = Utils.envOrDefault("CBB_USERNAME", "Administrator");
    public static final String CBB_PASSWORD = Utils.envOrDefault("CBB_PASSWORD", "password");

    public static final EventBus EVENT_BUS;

    public static final JsonSerializer SERIALIZER;

    protected static final Map<Class, Map<String, Object>> LOCAL_BEANS = Collections.synchronizedMap(new HashMap<>());
    protected static final Map<Class, Map<String, Object>> GLOBAL_BEANS = Collections.synchronizedMap(new HashMap<>());
    protected static final Map<Object, Void> OWNED = Collections.synchronizedMap(new WeakHashMap<>());
    protected static final Map<Object, String> KEY = Collections.synchronizedMap(new WeakHashMap<>());

    static {
        Utils.MAPPER.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        Utils.MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        CLUSTER = Cluster.connect(
                CBB_CLUSTER,
                ClusterOptions.clusterOptions(CBB_USERNAME, CBB_PASSWORD).environment(ClusterEnvironment.builder().jsonSerializer(
                        JacksonJsonSerializer.create(Utils.MAPPER)
                ).build())
        );
        BUCKET = CLUSTER.bucket(CBB_BUCKET);
        SCOPE = BUCKET.scope(CBB_SCOPE);
        EVENT_BUS = CLUSTER.environment().eventBus();
        SERIALIZER = SCOPE.environment().jsonSerializer();
    }

    public static <T> Optional<T> get(Class<T> type, String id) {
        return Utils.fetchObject(type, id);
    }

    public static <T> Optional<T> firstChild(Object bean, Class<T> type) {
        return Utils.children(bean, type)
                .stream()
                .map(BeanLink::target)
                .map(type::cast)
                .findFirst();
    }

    public static <T> Stream<T> allLinked(Object bean, Class<T> type) {
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
        Utils.ensureCollectionExists(bean.getClass());
        SCOPE.collection(Utils.collectionName(bean.getClass())).upsert(key, bean);
        return key;
    }

    public static CompletableFuture<Void> delete(Object bean) {
        return CompletableFuture.supplyAsync(() -> {
            SCOPE.collection(Utils.collectionName(bean.getClass())).remove(key(bean));
            return null;
        });
    }

    public static <S, T> CompletableFuture<BeanLink<S, T>> link(S source, T target) {
        return CompletableFuture.supplyAsync(() -> Utils.linkBetween(source, target).orElseGet(() -> {
            BeanLink<S, T> result = new BeanLink<S, T>(source, target);
            store(result);
            return result;
        }));
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
}
