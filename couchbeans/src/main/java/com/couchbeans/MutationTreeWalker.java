package com.couchbeans;

import org.gradle.configurationcache.problems.PropertyTrace;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class MutationTreeWalker {
    protected static void processBeanUpdate(Object bean, String source) {
        BeanInfo info = Couchbeans.firstLinked(bean, BeanInfo.class).orElse(null);
        if (info == null) {
            createBeanInfo(bean, source);
            info.updateAppliedSource(source);
            return;
        }
        List<String> changedFields = info.detectChangedFields(source);
        List<BeanLink> parents = Utils.parents(bean);
        BeanContext ctx = new BeanContext();
        ctx.add(bean);
        final Set<String> processedMethods = new HashSet<>();
        parents.stream()
                .forEach(parentLink -> {
                    Object target = parentLink.source();
                    Utils.inheritanceChain(target.getClass()).forEach(targetIdentity -> {
                        Supplier<List<BeanMethod>> methods = () -> BeanMethod.findSubscribers(parentLink.sourceType(), "update%", ctx.identities(), processedMethods);

                        for (List<BeanMethod> subscribers = methods.get(); !subscribers.isEmpty(); subscribers = methods.get()) {
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
    private static BeanInfo createBeanInfo(Object bean, String source) {
        BeanContext ctx = new BeanContext();
        ctx.add(bean);

        String beanKey = Couchbeans.KEY.get(bean);
        if (beanKey == null) {
            throw new IllegalStateException("Unknown bean");
        }

        final Set<String> processedMethods = new HashSet<>();
        Supplier<List<BeanMethod>> methods = () -> BeanMethod.findConstructors(ctx.identities(), processedMethods);

        for (List<BeanMethod> constructors = methods.get(); !constructors.isEmpty(); constructors = methods.get()) {
            for (BeanMethod methodInfo : constructors) {
                processedMethods.add(Couchbeans.key(methodInfo));
                ctx.addAll(methodInfo.construct(ctx));
            }
        }

        BeanInfo bi = new BeanInfo(bean);
        bi.updateAppliedSource(source);
        Couchbeans.store(bi);
        Couchbeans.link(bean, bi);
        return bi;
    }
}
