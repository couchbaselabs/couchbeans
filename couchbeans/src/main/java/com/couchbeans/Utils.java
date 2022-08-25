package com.couchbeans;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbeans.annotations.External;
import com.couchbeans.annotations.Global;
import com.couchbeans.annotations.Local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    public static String collectionName(Class from) {
        return from.getCanonicalName().replaceAll("\\.", "-");
    }

    public static Class collectionClass(String collectionName) throws ClassNotFoundException {
        return Class.forName(collectionName.replaceAll("-", "."));
    }

    public static String collectionRef(String collection) {
        return String.format("`%s`.`%s`.`%s`", Couchbeans.CBB_BUCKET, Couchbeans.CBB_SCOPE, collection);
    }

    public static String inlineCollectionRef(String collection) {
        return String.format("%s_%s_%s", Couchbeans.CBB_BUCKET, Couchbeans.CBB_SCOPE, collection);
    }

    public static void createCollectionIfNotExists(String collection) {
        Couchbeans.CLUSTER.query(String.format("CREATE COLLECTION %s IF NOT EXISTS", collectionRef(collection)));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createCollectionIfNotExists(Class type) {
        createCollectionIfNotExists(collectionName(type));
    }

    public static void createPrimaryIndexIfNotExists(String collection) {
        Couchbeans.CLUSTER.query(String.format("CREATE PRIMARY INDEX `idx_%s_primary` IF NOT EXISTS ON %s",
                inlineCollectionRef(collection),
                collectionRef(collection)
        ));
    }

    public static String nameList(String... fields) {
        return Arrays.stream(fields)
                .collect(Collectors.joining(","));
    }

    public static void createIndexIfNotExists(String collection, String... fields) {
        Couchbeans.CLUSTER.query(String.format("CREATE INDEX `idx_%s` IF NOT EXISTS ON %s(%s) USING GSI",
                inlineCollectionRef(collection),
                collectionRef(collection),
                nameList(fields)
        ));
    }

    private static final Map<String, String> ENV = new HashMap<>(System.getenv());

    public static void envOverride(String name, String value) {
        ENV.put(name, value);
    }

    public static String envOrDefault(String name, String defaultValue) {
        return ENV.getOrDefault(name, defaultValue);
    }

    public static List<BeanLink> children(Object source) {
        String sourceKey = Couchbeans.KEY.get(source);
        if (sourceKey == null) {
            throw new IllegalArgumentException("Unknown bean: " + source);
        }

        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2",
                        collectionRef(collectionName(source.getClass()))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        source.getClass().getCanonicalName(),
                        sourceKey
                ))
        ).rowsAs(BeanLink.class);
    }

    public static <S> List<BeanLink> parents(S source) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE targetType = $1 AND targetKey = $2",
                        collectionRef(collectionName(source.getClass()))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        source.getClass().getCanonicalName(),
                        Couchbeans.key(source)
                ))
        ).rowsAs(BeanLink.class);
    }

    public static <S, T> List<BeanLink<S, T>> parents(S source, Class<T> type) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE targetType = $1 AND targetKey = $2 AND sourceType = $3",
                        collectionRef(collectionName(source.getClass()))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        source.getClass().getCanonicalName(),
                        Couchbeans.key(source),
                        type.getCanonicalName()
                ))
        ).rowsAs((Class<BeanLink<S, T>>) (Class<?>) BeanLink.class);
    }


    public static <S, T> List<BeanLink<S, T>> children(Class<S> beanType, String sourceKey, Class<T> target) {
        Class<BeanLink<S, T>> linkType = (Class<BeanLink<S, T>>) (Class<?>) BeanLink.class;
        return (List<BeanLink<S, T>>) Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2 AND targetType = $3",
                        collectionRef(collectionName(BeanLink.class))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        beanType.getCanonicalName(),
                        sourceKey,
                        target.getCanonicalName()
                ))
        ).rowsAs(linkType);
    }

    public static <S, T> List<BeanLink<S, T>> children(S source, Class<T> target) {
        return children((Class<S>)source.getClass(), Couchbeans.key(source), target);
    }

    public static List<BeanMethod> findConsumers(String beanType, String name, JsonArray arguments, JsonArray except) {
        return Optional.ofNullable(
                        Couchbeans.SCOPE.query(
                                String.format("SELECT * FROM %s WHERE `beanType` LIKE $1 AND `name` LIKE $2 AND EVERY v IN `arguments` SATISFIES v IN $3 END AND META().id NOT IN $4", collectionRef(collectionName(BeanMethod.class))),
                                QueryOptions.queryOptions().parameters(JsonArray.from(
                                        beanType, name, arguments, except
                                ))
                        )
                ).map(gr -> gr.rowsAs(BeanMethod.class))
                .orElseGet(Collections::emptyList);
    }

    public static Stream<BeanMethod.Arguments> matchMethods(Class source, String methodNamePattern, Object[] path) {
        return Couchbeans.SCOPE.query(
                        String.format("SELECT * FROM %s WHERE `beanType` LIKE $1 AND `name` LIKE $2", collectionRef(collectionName(BeanMethod.class))),
                        QueryOptions.queryOptions().parameters(JsonArray.from(
                                source.getCanonicalName(),
                                methodNamePattern
                        ))
                ).rowsAs(BeanMethod.class).stream()
                .map(method -> {
                    List<Object> arguments = matchMethod(method, path);
                    if (arguments != null) {
                        return method.new Arguments(arguments);
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    public static Stream<BeanMethod.Arguments> matchConstructors(Object[] path) {
        return matchConstructors(path, new String[0]);
    }

    public static Stream<BeanMethod.Arguments> matchConstructors(Object[] path, String[] exclude) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE `name` = '<init>' AND META().id NOT IN $1", collectionRef(collectionName(BeanMethod.class))),
                QueryOptions.queryOptions().parameters(JsonArray.from(exclude))
        ).rowsAs(BeanMethod.class).stream()
                .map(constructor -> {
                    List<Object> arguments = matchMethod(constructor, path);
                    if (arguments != null) {
                        return constructor.new Arguments(arguments);
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    public static boolean matchTypes(Class t1, Class t2) {
        return  t1.isAssignableFrom(t2) || (
                t1.isArray() && t1.componentType().isAssignableFrom(t2)
        );
    }

    public static List<Object> matchMethod(BeanMethod method, Object[] path) {
        AtomicReference<Class> lastArgumentType = new AtomicReference<>();
        List<String> argumentTypes = method.arguments();
        List<Object> arguments = new ArrayList<>();
        boolean matches = Arrays.stream(path)
                .map(pathBean -> {
                    if (argumentTypes.size() == 0) {
                        return false;
                    }
                    Class pattern = null;
                    try {
                        pattern = Class.forName(argumentTypes.remove(0));
                    } catch (ClassNotFoundException e) {
                        BeanException.report(method, e);
                        return false;
                    }
                    Class beanType = pathBean.getClass();

                    if (matchTypes(pattern, beanType)) {
                        if (pattern.isArray()) {
                            lastArgumentType.set(pattern);
                        } else {
                            lastArgumentType.set(null);
                        }
                        return true;
                    } else {
                        return lastArgumentType.get() != null &&
                                lastArgumentType.get().isArray() && matchTypes(lastArgumentType.get(), beanType);
                    }
                }).allMatch(Boolean.TRUE::equals);

        if (matches) {
            return arguments;
        }
        return null;
    }

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    public static <T> Optional<T> fetchObject(Class<T> type, String key) {
        return Optional.ofNullable(Couchbeans.SCOPE.collection(collectionName(type)))
                .map(c -> c.get(key))
                .filter(Objects::nonNull)
                .map(gr -> gr.contentAs(type));
    }

    protected static Set<String> inheritanceDescriptorChain(Class of) {
        if (of != Object.class) {
            Set<String> parent = inheritanceDescriptorChain(of.getSuperclass());
            parent.add(of.descriptorString());
            return parent;
        }
        HashSet<String> root = new HashSet<>();
        root.add(Object.class.descriptorString());
        return root;
    }

    public static Set<String> inheritanceChain(Class of) {
        if (of != Object.class) {
            Set<String> parent = inheritanceChain(of.getSuperclass());
            parent.add(of.getCanonicalName());
            return parent;
        }

        HashSet<String> root = new HashSet<>();
        root.add(Object.class.getCanonicalName());
        return root;
    }

    public static boolean hasLocalDataService() {
        // todo: implement local data service detection
        return false;
    }

    public static <S, T> Optional<BeanLink<S, T>> linkBetween(S source, T target) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2 AND targetType = $3 AND targetKey = $4", collectionRef(collectionName(BeanLink.class))),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        source.getClass().getCanonicalName(), Couchbeans.key(source),
                        target.getClass().getCanonicalName(), Couchbeans.key(target)
                ))
        ).rowsAs((Class<BeanLink<S, T>>)((Class<?>)BeanLink.class)).stream().findFirst();
    }

    public static BeanType beanType(Object bean) {
        Class type = bean.getClass();
        if (type.isAnnotationPresent(Global.class)) {
            return BeanType.GLOBAL;
        } else if (type.isAnnotationPresent(External.class)) {
            return BeanType.EXTERNAL;
        } else if (type.isAnnotationPresent(Local.class)) {
            return BeanType.LOCAL;
        } else if (Modifier.isProtected(type.getModifiers())) {
            return BeanType.INTERNAL;
        }
        return BeanType.NORMAL;
    }

    public static Optional<Method> getSetter(Class type, String key) {
        try {
            Field field = type.getField(key);
            Class fieldType = field.getType();
            try {
                return Optional.of(type.getMethod(key, fieldType));
            } catch (NoSuchMethodException e) {
                try {
                    return Optional.of(
                            type.getMethod(
                                    "set" + key.substring(0, 1).toUpperCase(Locale.ROOT) +
                                            ((key.length() > 1) ? key.substring(1) : ""),
                                    fieldType
                            ));
                } catch (NoSuchMethodException ex) {
                    return Optional.empty();
                }
            }
        } catch (NoSuchFieldException e) {
            return Optional.empty();
        }
    }

    public static Optional<BeanInfo> getBeanInfo(String beanType, String beanKey) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE `beanType` = $1 AND `beanKey` = $2 LIMIT 1", collectionRef(collectionName(BeanInfo.class))),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        beanType, beanKey
                ))
        ).rowsAs(BeanInfo.class).stream().findFirst();
    }

    public static boolean hasParents(String type, String key) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT 1 FROM %s WHERE `targetType` = $1 AND `targetKey` = $2 LIMIT 1", collectionRef(collectionName(BeanLink.class))),
                QueryOptions.queryOptions().parameters(JsonArray.from(type, key))
        ).rowsAsObject().stream().findAny().isPresent();
    }
}
