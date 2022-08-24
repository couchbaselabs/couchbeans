package com.couchbeans;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MutationTreeWalker {
    protected static void processBeanUpdate(Object bean) {
        BeanInfo info = Couchbeans.firstLinked(bean, BeanInfo.class).orElse(null);
        if (info == null) {
            createBeanInfo(bean);
            return;
        }
        List<BeanLink> children = Utils.findLinkedBeans(bean);
        BeanContext ctx = new BeanContext();
        ctx.add(bean);
        final Set<String> processedMethods = new HashSet<>();
        children.stream()
                .forEach(childLink -> {
                    Object target = childLink.target();
                    Utils.inheritanceChain(target.getClass()).forEach(targetIdentity -> {
                        for (List<BeanMethod> subscribers = BeanMethod.findSubscribers(childLink.targetType(), ctx.identities(), processedMethods); !subscribers.isEmpty(); subscribers = BeanMethod.findSubscribers(childLink.targetType(), ctx.identities(), processedMethods)) {
                            for (BeanMethod methodInfo : subscribers) {
                                processedMethods.add(Couchbeans.key(methodInfo));
                                ctx.addAll(methodInfo.apply(target, ctx));
                            }
                        }
                    });
                });
    }

    /**
     * This method is called on new beans when no BeanInfo object
     * for a bean was found.
     *
     * @param bean
     * @return
     */
    private static BeanInfo createBeanInfo(Object bean) {
        BeanContext ctx = new BeanContext();
        ctx.add(bean);

        String beanKey = Couchbeans.KEY.get(bean);
        if (beanKey == null) {
            throw new IllegalStateException("Unknown bean");
        }

        final Set<String> processedMethods = new HashSet<>();

        for (List<BeanMethod> constructors = BeanMethod.findConstructors(ctx.identities(), processedMethods); !constructors.isEmpty(); constructors = BeanMethod.findConstructors(ctx.identities(), processedMethods)) {
            for (BeanMethod methodInfo : constructors) {
                processedMethods.add(Couchbeans.key(methodInfo));
                ctx.addAll(methodInfo.construct(ctx));
            }
        }

        BeanInfo bi = new BeanInfo(bean);
        Couchbeans.store(bi);
        Couchbeans.link(bean, bi);
        return bi;
    }
}
