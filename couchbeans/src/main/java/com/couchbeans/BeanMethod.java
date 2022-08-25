package com.couchbeans;

import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BeanMethod {
    private String beanType;
    private String name;
    private List<String> arguments;

    private String hash;

    public BeanMethod() {

    }

    public BeanMethod(String beanType, String name, List<String> arguments) {
        this.beanType = beanType;
        this.name = name;
        this.arguments = arguments;

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(toString().getBytes());
            this.hash = new BigInteger(1, messageDigest.digest()).toString(34);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public BeanMethod(String className, String name) {
        this(className, name, Collections.EMPTY_LIST);
    }

    public BeanMethod(JsonObject source) {
        this(source.getString("beanType"), source.getString("name"), new LinkedList<>());
        source.getArray("arguments").forEach(s -> arguments.add((String) s));
    }

    public String beanType() {
        return beanType;
    }

    public String name() {
        return name;
    }

    public List<String> arguments() {
        return new ArrayList<>(arguments);
    }

    @Override
    public String toString() {
        return new StringBuilder(this.beanType)
                .append("::")
                .append(name)
                .append(arguments.stream().collect(Collectors.joining(",", "(", ")")))
                .toString();
    }

    public String getHash() {
        return hash;
    }

    public JsonObject toJsonObject() {
        JsonObject result = JsonObject.create();
        result.put("beanType", beanType);
        result.put("name", name);
        JsonArray args = JsonArray.create();
        arguments.stream().forEach(args::add);
        result.put("arguments", arguments);
        return result;
    }

    public static List<BeanMethod> findConstructors(Collection<String> arguments, Collection<String> except) {
        return findConstructors("%", arguments, except);
    }
    public static List<BeanMethod> findConstructors(String ofType, Collection<String> arguments, Collection<String> except) {
        JsonArray args = JsonArray.create();
        arguments.forEach(args::add);
        JsonArray ex = JsonArray.create();
        except.forEach(ex::add);
        return Utils.findConsumers(ofType, "<init>", args, ex);
    }

    public static List<BeanMethod> findSubscribers(Collection<String> arguments, Collection<String> except) {
        return findSubscribers("%", arguments, except);
    }

    public static List<BeanMethod> findSubscribers(String owningType, Collection<String> arguments, Collection<String> except) {
        return findSubscribers(owningType, "%", arguments, except);
    }
    public static List<BeanMethod> findSubscribers(String owningType, String name, Collection<String> arguments, Collection<String> except) {
        JsonArray args = JsonArray.create();
        arguments.forEach(args::add);
        JsonArray ex = JsonArray.create();
        except.forEach(ex::add);
        return Utils.findConsumers(owningType, name, args, ex);
    }

    public List<Object> apply(Object target, List<Object> args) {
        try {
            Class[] arguments = args.stream().map(Object::getClass).toArray(Class[]::new);
            Class methodClass = Class.forName(beanType);
            if (methodClass.isAssignableFrom(target.getClass())) {
                throw new IllegalArgumentException("Method that belongs to type '" + beanType + "' cannot be invoked on bean of type '" + target.getClass().getCanonicalName() + "'");
            }
            Method method = target.getClass().getMethod(name, arguments);
            Object result = method.invoke(target, args);
            List cResult;
            if (result instanceof Collection) {
                cResult = new ArrayList((Collection) result);
            } else {
                cResult = Arrays.asList(result);
            }

            cResult.forEach(b -> {
                Couchbeans.store(b);
                Couchbeans.link(target, b);
                args.stream().forEach(a -> Couchbeans.link(a, b));
            });
            return cResult;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Object> apply(Object target, BeanContext ctx) {
        try {
            return invoke(ctx, args -> apply(target, args));
        } catch (Exception e) {
            BeanException exception = new BeanException(e);
            Couchbeans.store(exception);
            Couchbeans.link(target, exception);
            Couchbeans.link(this, exception);
        }
        return Collections.EMPTY_LIST;
    }

    public List<Object> construct(BeanContext ctx) {
        try {
            Class target = Class.forName(beanType);
            return invoke(ctx, args -> {
                try {
                    Class[] arguments = args.stream().map(Object::getClass).toArray(Class[]::new);
                    Object childBean = target.getConstructor(arguments).newInstance(args.toArray());
                    Couchbeans.store(childBean).exceptionally(e -> {
                        throw new RuntimeException("Failed to store child bean of type '" + target + "'", e);
                    }).join();
                    args.forEach(bean -> {
                        Couchbeans.link(bean, childBean).exceptionally(e -> {
                            throw new RuntimeException("Failed to link child bean '" + Couchbeans.ref(childBean) + "' to parent '" + Couchbeans.ref(bean) + "'");
                        }).join();
                    });
                    return Arrays.asList(childBean);
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            BeanException exception = new BeanException(e);
            Couchbeans.store(exception);
            Couchbeans.link(this, exception);
        }
        return Collections.EMPTY_LIST;
    }

    public List<Object> invoke(BeanContext ctx, Function<List<Object>, List<Object>> invoker) {
        return ctx.resolveArguments(arguments).stream()
                .map(invoker)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }


    public class Arguments {
        private List<Object> arguments;

        public Arguments(List<Object> arguments) {
            this.arguments = arguments;
        }

        public List<Object> arguments() {
            return arguments;
        }

        public BeanMethod method() {
            return BeanMethod.this;
        }

        public List<Object> apply(Object target) {
            return BeanMethod.this.apply(target, arguments);
        }
    }
}