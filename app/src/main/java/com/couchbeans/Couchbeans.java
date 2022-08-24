package com.couchbeans;

import com.couchbase.client.core.cnc.EventBus;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.codec.JsonSerializer;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;

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
    protected static final Map<Object, String> KEY = Collections.synchronizedMap(new WeakHashMap<>());

    static {
       CLUSTER = Cluster.connect(
               CBB_CLUSTER,
               CBB_USERNAME,
               CBB_PASSWORD
       );
       BUCKET = CLUSTER.bucket(CBB_BUCKET);
       SCOPE = BUCKET.scope(CBB_SCOPE);
       EVENT_BUS = CLUSTER.environment().eventBus();
       SERIALIZER = SCOPE.environment().jsonSerializer();
    }

    public static <T> Optional<T> get(Class<T> type, String id) {
        return Utils.fetchObject(type, id);
    }

    public static <T> Optional<T> firstLinked(Object bean, Class<T> type) {
        return Utils.findLinkedBeans(bean, type)
                .stream()
                .map(BeanLink::target)
                .map(type::cast)
                .findFirst();
    }

    public static <T> Stream<T> allLinked(Object bean, Class<T> type) {
        return Utils.findLinkedBeans(bean, type)
                .stream()
                .map(type::cast);
    }

    public static String key(Object bean) {
        synchronized (KEY) {
            if (!KEY.containsKey(bean)) {
                KEY.put(bean, UUID.randomUUID().toString());
            }
        }
        return KEY.get(bean);
    }

    public static CompletableFuture<Void> store(Object bean) {
        key(bean);
        return CompletableFuture.failedFuture(new Exception("Not implemented yet"));
    }

    public static CompletableFuture<Void> delete(Object bean) {
        return CompletableFuture.supplyAsync(() -> {
            SCOPE.collection(Utils.collectionName(bean.getClass())).remove(key(bean));
            return null;
        });
    }

    public static CompletableFuture<BeanLink> link(Object source, Object target) {
        return CompletableFuture.supplyAsync(() -> {
            BeanLink result = new BeanLink(source, target);
            store(result).join();
            return result;
        });
    }

    public static String ref(Object bean) {
        return String.format("couchbean://%s/%s", bean.getClass().getCanonicalName(), key(bean));
    }
}
