package cbb;

import cbb.annotations.Scope;
import com.couchbase.client.dcp.deps.io.netty.handler.codec.serialization.ObjectEncoder;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import cbb.annotations.Index;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    public final static String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> ENV = new HashMap<>(System.getenv());

    public static String collectionName(Class from) {
        return collectionName(from.getCanonicalName());
    }

    public static Optional<Class> collectionClass(String collectionName) {
        return Couchbeans.getBeanType(collectionName.replaceAll("-", "."));
    }

    public static String collectionName(String typeName) {
        return typeName.replaceAll("\\.", "-");
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

    public static void envOverride(String name, String value) {
        ENV.put(name, value);
    }

    public static String envOrDefault(String name, String defaultValue) {
        return ENV.getOrDefault(name, defaultValue);
    }

    public static List<BeanLink> children(Object source) {
        String sourceType = source.getClass().getCanonicalName();
        String sourceKey = Couchbeans.key(source);

        List<BeanLink> result = Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2",
                        collectionRef(collectionName(source.getClass()))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        sourceType,
                        sourceKey
                ))
        ).rowsAs(BeanLink.class);

        allMemLinkKeys(sourceType, sourceKey, Couchbeans.LINK_INDEX)
                .map(lk -> Couchbeans.get(BeanLink.class, lk))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::add);

        return result;
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
        List<BeanLink<S, T>> result = Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE targetType = $1 AND targetKey = $2 AND sourceType = $3",
                        collectionRef(collectionName(source.getClass()))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        source.getClass().getCanonicalName(),
                        Couchbeans.key(source),
                        type.getCanonicalName()
                ))
        ).rowsAs((Class<BeanLink<S, T>>) (Class<?>) BeanLink.class);

        allMemLinkKeys(source.getClass().getCanonicalName(), type.getCanonicalName(), Couchbeans.REVERSE_LINK_INDEX)
                .map(k -> Couchbeans.get(BeanLink.class, k))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(l -> (BeanLink<S, T>) l)
                .forEach(result::add);
        return result;
    }

    public static <S, T> List<BeanLink<S, T>> children(Class<S> beanType, String sourceKey, Class<T> target) {
        Class<BeanLink<S, T>> linkType = (Class<BeanLink<S, T>>) (Class<?>) BeanLink.class;
        List<BeanLink<S, T>> result = (List<BeanLink<S, T>>) Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2 AND targetType = $3",
                        collectionRef(collectionName(BeanLink.class))
                ),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        beanType.getCanonicalName(),
                        sourceKey,
                        target.getCanonicalName()
                ))
        ).rowsAs(linkType);

        allMemLinkKeys(beanType.getCanonicalName(), sourceKey, target.getCanonicalName(), Couchbeans.LINK_INDEX)
                .map(Couchbeans::getLink)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::add);

        return result;
    }

    public static <S, T> List<BeanLink<S, T>> children(S source, Class<T> target) {
        return children((Class<S>) source.getClass(), Couchbeans.key(source), target);
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
                        QueryOptions.queryOptions().parameters(JsonArray.from((Object) exclude))
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
        return t1.isAssignableFrom(t2) || (
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

    public static <T> Optional<T> fetchObject(Class<T> type, String key) {
        return (Optional<T>) fetchObject(type.getCanonicalName(), key);
    }

    public static Optional fetchObject(String type, String key) {
        return Optional.ofNullable(Couchbeans.SCOPE.collection(collectionName(type)))
                .map(c -> c.get(key))
                .filter(Objects::nonNull)
                .map(gr -> gr.contentAs(Couchbeans.getBeanType(type).get()));
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
        String tKey = Couchbeans.key(target);
        return allMemLinkKeys(source.getClass().getCanonicalName(), Couchbeans.key(source), target.getClass().getCanonicalName(), Couchbeans.LINK_INDEX)
                .map(Couchbeans::getLink)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(l -> tKey.equals(l.targetKey()))
                .map(l -> Optional.of((BeanLink<S, T>) l))
                .findFirst()
                .orElseGet(() -> Couchbeans.SCOPE.query(
                        String.format("SELECT * FROM %s WHERE sourceType = $1 AND sourceKey = $2 AND targetType = $3 AND targetKey = $4", collectionRef(collectionName(BeanLink.class))),
                        QueryOptions.queryOptions().parameters(JsonArray.from(
                                source.getClass().getCanonicalName(), Couchbeans.key(source),
                                target.getClass().getCanonicalName(), Couchbeans.key(target)
                        ))
                ).rowsAs((Class<BeanLink<S, T>>) ((Class<?>) BeanLink.class)).stream().findFirst());
    }

    public static BeanScope scope(Object bean) {
        Class type = bean.getClass();
        return (type.isAnnotationPresent(Scope.class)) ? ((Scope) type.getAnnotation(Scope.class)).value() : BeanScope.BUCKET;
    }

    public static Optional<Method> getSetter(Class type, Field field) {
        String key = field.getName();
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
    }

    public static Optional<BeanInfo> getBeanInfo(String beanType, String beanKey) {
        try {
            return Couchbeans.SCOPE.query(
                    String.format("SELECT * FROM %s WHERE `beanType` = $1 AND `beanKey` = $2 LIMIT 1", collectionRef(collectionName(BeanInfo.class))),
                    QueryOptions.queryOptions().parameters(JsonArray.from(
                            beanType, beanKey
                    ))
            ).rowsAs(BeanInfo.class).stream().findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static boolean hasParents(String type, String key) {
        return allMemLinkKeys(type, key, Couchbeans.REVERSE_LINK_INDEX)
                .map(lk -> true)
                .findFirst()
                .orElseGet(() -> Couchbeans.SCOPE.query(
                        String.format("SELECT 1 FROM %s WHERE `targetType` = $1 AND `targetKey` = $2 LIMIT 1", collectionRef(collectionName(BeanLink.class))),
                        QueryOptions.queryOptions().parameters(JsonArray.from(type, key))
                ).rowsAsObject().stream().findAny().isPresent());
    }

    public static Collection ensureCollectionExists(Class<?> aClass) {
        String name = collectionName(aClass);
        String[] indexFields = getFields(aClass)
                .filter(field -> field.isAnnotationPresent(Index.class))
                .map(Field::getName)
                .toArray(String[]::new);

        createCollectionIfNotExists(name);
        createPrimaryIndexIfNotExists(name);
        if (indexFields.length > 0) {
            createIndexIfNotExists(name, indexFields);
        }
        return Couchbeans.SCOPE.collection(name);
    }

    private static Stream<Field> getFields(Class<?> aClass) {
        if (Object.class == aClass) {
            return Stream.empty();
        }

        return Streams.concat(Arrays.stream(aClass.getDeclaredFields()), getFields(aClass.getSuperclass()));
    }

    public static Optional<Collection> getExistingCollection(String name) {
        return Couchbeans.BUCKET.collections().getAllScopes().stream()
                .filter(scope -> Couchbeans.CBB_SCOPE.equals(scope.name()))
                .flatMap(scope -> scope.collections().stream())
                .filter(coll -> name.equals(coll.name()))
                .findFirst().map(coll -> Couchbeans.SCOPE.collection(name));
    }

    public static Stream<CtField> getFields(CtClass ctClass) {
        if (Object.class.getCanonicalName().equals(ctClass.getName())) {
            return Stream.empty();
        }
        try {
            return Streams.concat(Arrays.stream(ctClass.getDeclaredFields()), getFields(ctClass.getSuperclass()));
        } catch (NotFoundException e) {
            return Stream.empty();
        }
    }

    public static Collection ensureCollectionExists(CtClass ctClass) {
        String name = collectionName(ctClass);
        return getExistingCollection(name).orElseGet(() -> {
            String[] indexFields = getFields(ctClass)
                    .filter(field -> field.hasAnnotation(Index.class))
                    .map(CtField::getName)
                    .toArray(String[]::new);

            createCollectionIfNotExists(name);
            createPrimaryIndexIfNotExists(name);
            if (indexFields.length > 0) {
                createIndexIfNotExists(name, indexFields);
            }
            return Couchbeans.SCOPE.collection(name);
        });
    }

    private static String collectionName(CtClass ctClass) {
        return collectionName(ctClass.getName());
    }

    public static int injectClassloader(Class main, String[] args) {
        if (System.getProperty("java.system.class.loader", "").equals(CouchbaseClassLoader.class.getCanonicalName())) {
            return -1;
        }

        List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        String[] call = new String[inputArguments.size() + args.length + 5];
        call[0] = javaBin;
        call[1] = "-Djava.system.class.loader=" + CouchbaseClassLoader.class.getCanonicalName();
        call[2] = "-cp";
        call[3] = System.getProperty("java.class.path");
        System.arraycopy(inputArguments.toArray(), 0, call, 4, inputArguments.size());
        call[inputArguments.size() + 4] = main.getCanonicalName();
        System.arraycopy(args, 0, call, inputArguments.size() + 5, args.length);

        System.out.println("Restarting JVM with couchbase classloader...");
        try {
            ProcessBuilder pb = new ProcessBuilder(call);
            System.out.println("Command: " + pb.command().stream().collect(Collectors.joining(" ")));
            pb.environment().putAll(ENV);
            pb.redirectInput();
            pb.redirectOutput();
            pb.redirectError();
            Process p = null;
            try {
                pb.inheritIO().start();
                System.exit(0);
                return 0;
            } catch (Exception e) {
                p.destroyForcibly();
                return 13;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    public static Optional<String> env(String key) {
        if (!ENV.containsKey(key)) {
            return Optional.empty();
        }
        return Optional.of(ENV.get(key));
    }

    public static List<BeanLink> allChildLinks(String beanType, String key) {
        List<BeanLink> result = Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE `sourceType` = $1 AND `sourceKey` = $2", collectionRef(beanType)),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        beanType, key
                ))
        ).rowsAs(BeanLink.class);

        allMemLinkKeys(beanType, key, Couchbeans.LINK_INDEX)
                .map(k -> Couchbeans.get(BeanLink.class, k))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::add);

        return result;
    }

    public static List<BeanLink> allParentLinks(String beanType, String key) {
        List<BeanLink> result = Couchbeans.SCOPE.query(
                String.format("SELECT * FROM %s WHERE `targetType` = $1 AND `targetKey` = $2", collectionRef(beanType)),
                QueryOptions.queryOptions().parameters(JsonArray.from(
                        beanType, key
                ))
        ).rowsAs(BeanLink.class);

        allMemLinkKeys(beanType, key, Couchbeans.REVERSE_LINK_INDEX)
                .map(k -> Couchbeans.get(BeanLink.class, k))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(result::add);

        return result;
    }

    public static Stream<String> allMemLinkKeys(String beanType, String key, Map<String, Map<String, Map<String, Set<String>>>> index) {
        return index.getOrDefault(beanType, (Map<String, Map<String, Set<String>>>) Collections.EMPTY_MAP)
                .getOrDefault(key, (Map<String, Set<String>>) Collections.EMPTY_MAP)
                .values().stream()
                .flatMap(Set::stream);
    }

    public static Stream<String> allMemLinkKeys(String beanType, String key, String otherType, Map<String, Map<String, Map<String, Set<String>>>> index) {
        return index.getOrDefault(beanType, (Map<String, Map<String, Set<String>>>) Collections.EMPTY_MAP)
                .getOrDefault(key, (Map<String, Set<String>>) Collections.EMPTY_MAP)
                .getOrDefault(otherType, (Set<String>) Collections.EMPTY_SET)
                .stream();
    }

    public static List<BeanLink> allParentLinks(Object bean) {
        return allParentLinks(bean.getClass().getCanonicalName(), Couchbeans.key(bean));
    }

    public static List<BeanLink> allChildLinks(Object bean) {
        return allChildLinks(bean.getClass().getCanonicalName(), Couchbeans.key(bean));
    }

    public static void updateBean(Object bean, String oldSource, String newSource, boolean callSetters) {
        try {
            Class type = bean.getClass();
            Map<String, Object> oldValues = JsonObject.fromJson(oldSource).toMap();
            Map<String, Object> newValues = JsonObject.fromJson(newSource).toMap();
            Object clone = Utils.MAPPER.readValue(newSource, type);
            Streams.concat(oldValues.keySet().stream(), newValues.keySet().stream())
                    .distinct()
                    .filter(key ->
                            newValues.containsKey(key) != oldValues.containsKey(key) ||
                                    !Objects.equals(newValues.get(key), oldValues.get(key))
                    )
                    .forEach(key -> getField(type, key).ifPresent(field -> {
                                Object value = getFieldValue(clone, field);
                                if (callSetters) {
                                    setFieldValueUsingMethods(bean, field, value);
                                } else {
                                    setFieldValue(bean, field, value);
                                    invokeValueHandler(bean, field, value);
                                }
                            }
                    ));
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getFieldValue(Object source, Field field) {
        try {
            field.setAccessible(true);
            return field.get(source);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setFieldValue(Object target, Field field, Object value) {
        field.setAccessible(true);
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Optional<Field> getField(Class from, String key) {
        try {
            return Optional.of(from.getDeclaredField(key));
        } catch (NoSuchFieldException e) {
            Class sup = from.getSuperclass();
            if (sup != Object.class) {
                return getField(sup, key);
            }
            return Optional.empty();
        }
    }

    public static void setFieldValueUsingMethods(Object bean, Field field, Object value) {
        Class beanType = bean.getClass();
        Utils.getSetter(beanType, field).ifPresentOrElse(
                setter -> invokeSetter(bean, setter, value),
                () -> setFieldValue(bean, field, value)
        );
        invokeValueHandler(bean, field, value);
    }

    public static void invokeSetter(Object bean, Method setter, Object value) {
        try {
            setter.setAccessible(true);
            setter.invoke(bean, value);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void invokeValueHandler(Object bean, Field field, Object value) {
        String svalue = StringUtils.capitalize(String.valueOf(value));
        String fname = StringUtils.capitalize(field.getName());
        String hname;
        Class fieldType = field.getType();
        if (fieldType == Boolean.class || fieldType == boolean.class) {
            hname = String.format("when%s%s", Boolean.TRUE.equals(value) ? "" : "Not", fname);
        } else {
            hname = String.format("when%sIs%s", fname, svalue);
        }

        Utils.getMethod(bean.getClass(), hname).ifPresent(handler -> {
            handler.setAccessible(true);
            try {
                handler.invoke(bean);
            } catch (Exception e) {
                BeanException.report(bean, e);
            }
        });
    }

    private static Optional<Method> getMethod(Class<?> aClass, String name) {
        try {
            return Optional.of(aClass.getDeclaredMethod(name));
        } catch (NoSuchMethodException e) {
            Class sup = aClass.getSuperclass();
            if (sup != Object.class) {
                return getMethod(sup, name);
            }
            return Optional.empty();
        }
    }

    public static List<Singleton> getAllSingletons() {
        return Couchbeans.SCOPE.query(
                String.format("SELECT RAW `%s` FROM %s", collectionName(Singleton.class), collectionRef(collectionName(Singleton.class)))
        ).rowsAs(Singleton.class);
    }

    public static Object unwrapBean(Object bean) {
        if (bean instanceof Singleton) {
            return ((Singleton) bean).get();
        }
        return bean;
    }
}
