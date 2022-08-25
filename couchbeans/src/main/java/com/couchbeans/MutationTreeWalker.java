package com.couchbeans;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MutationTreeWalker {
    protected static void processBeanUpdate(BeanInfo info, String source) {
        try {
            processGraphChange(
                    info.update(source),
                    "updateParent%",
                    "updateChild%"
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static void processNewBeanLink(BeanLink link) {
        processBeanLink(link, "linkTo%", "linkChild%");
    }

    protected static void processDeletedBeanLink(BeanLink link) {
        processBeanLink(link, "unlinkFrom%", "unlinkChild%");
        if (!Utils.hasParents(link.targetType(), link.targetKey())) {
            Couchbeans.delete(link.target());
        }
    }

    protected static void processBeanLink(BeanLink link, String linkToMethodPattern, String linkChildMethodPattern) {
        processGraphChange(link.target(), linkToMethodPattern, linkChildMethodPattern);
    }

    protected static void processGraphChange(Object source, String childMethodNamePattern, String parentMethodNamePattern) {
        Object[][] parentPaths = propagateUp(source);
        Object[][] childPaths = propagateDown(source);
        try {
            Stream<Object[][]> pathStream;
            if (parentPaths.length > 0) {
                pathStream = Arrays.stream(parentPaths)
                        .flatMap(parentPath -> Arrays.stream(childPaths)
                                .map(childPath -> new Object[][]{parentPath, childPath})
                        );
            } else {
                pathStream = Arrays.stream(childPaths).map(childPath -> new Object[][]{new Object[0], childPath});
            }

            pathStream.forEach(path -> {
                for (int i = 0; i < path[0].length + path[1].length; i++) {
                    Object pathBean;
                    Object[] beanPath;
                    String methodNamePattern;
                    if (i < path[0].length - 2) {
                        // parent beans get to see only paths down to the target
                        pathBean = path[0][i];
                        beanPath = new Object[path[0].length - i - 1];
                        methodNamePattern = parentMethodNamePattern;
                        System.arraycopy(path[0], i + 1, beanPath, 0, beanPath.length);
                    } else if (i == path[0].length - 1) {
                        // this is the source and we don't want to handle it as parent
                        continue;
                    } else if (i == 0 && path[0].length == 0) {
                        // no parent paths, doesn't make sense to process source as child
                        continue;
                    } else {
                        pathBean = path[1][i - path[0].length];
                        beanPath = new Object[i - 1];
                        methodNamePattern = childMethodNamePattern;
                        if (i == path[0].length) {
                            beanPath = path[0];
                        } else {
                            System.arraycopy(path[0], 0, beanPath, 0, path[0].length);
                            System.arraycopy(path[1], 0, beanPath, path[0].length, beanPath.length - path[0].length);
                        }
                    }

                    Utils.matchMethods(pathBean.getClass(), methodNamePattern, beanPath).forEach(arguments -> applyMethod(pathBean, arguments));
                }

            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static Object[][] propagateUp(Object bean) {
        return propagateUp(bean, new Object[0]);
    }

    protected static Object[][] propagateUp(Object bean, Object[] path) {
        return propagate(bean, new String[0], path, b -> Utils.parents(b).stream().map(BeanLink::source), true);
    }

    protected static Object[][] propagateDown(Object bean, Object[] path) {
        return propagate(bean, new String[0], path, b -> Utils.children(b).stream().map(BeanLink::target), false);
    }

    protected static Object[][] propagateDown(Object bean) {
        return propagateDown(bean, new Object[0]);
    }

    protected static Object[][] propagate(Object bean, Function<Object, Stream<Object>> pathProvider) {
        return propagate(bean, new String[0], new Object[0], pathProvider, false);
    }

    protected static Object[][] propagate(Object bean, Function<Object, Stream<Object>> pathProvider, boolean invertPath) {
        return propagate(bean, new String[0], new Object[0], pathProvider, invertPath);
    }

    protected static Object[][] propagate(Object bean, String[] pathKeys, Object[] path, Function<Object, Stream<Object>> pathProvider, boolean invertPath) {
        String beanKey = Couchbeans.key(bean);
        Object[] subPath = new Object[path.length + 1];
        System.arraycopy(path, 0, subPath, invertPath ? 1 : 0, path.length);
        subPath[invertPath ? 0 : path.length] = bean;
        String[] subPathKeys = new String[path.length + 1];
        System.arraycopy(pathKeys, 0, subPathKeys, invertPath ? 1 : 0, pathKeys.length);
        subPathKeys[invertPath ? 0 : pathKeys.length] = beanKey;

        Object[][] paths = pathProvider.apply(bean)
                // prevent circular references
                .filter(b -> Arrays.stream(pathKeys).noneMatch(beanKey::equals))
                .flatMap(linkedBean -> Arrays.stream(propagate(linkedBean, subPathKeys, subPath, pathProvider, invertPath)))
                .toArray(Object[][]::new);

        if (paths.length == 0) {
            if (path.length > 0) {
                // leaf
                return new Object[][]{subPath};
            } else {
                // no paths to follow
                return new Object[0][];
            }
        } else {
            return paths;
        }
    }

    protected static void applyMethod(Object target, BeanMethod.Arguments arguments) {
        arguments.apply(target).forEach(newBean -> {
            // oh, we are branching...
            Couchbeans.store(newBean);
            arguments.arguments().forEach(argument -> Couchbeans.link(argument, newBean));
        });
    }

    /**
     * This method is called on new beans when no BeanInfo object
     * for a bean was found.
     */
    protected static BeanInfo registerBean(Class type, String key, String source) {
        BeanInfo info = new BeanInfo(type.getCanonicalName(), key, source);
        Couchbeans.store(info);
        return info;
    }
}
