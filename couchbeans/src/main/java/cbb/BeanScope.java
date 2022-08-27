package cbb;

import cbb.annotations.Scope;
import javassist.CtClass;

import java.lang.reflect.Method;

public enum BeanScope {
    MEMORY, BUCKET, NODE(true), GLOBAL(true);

    private final boolean isAutoCreated;
    BeanScope() {
        isAutoCreated = false;
    }
    BeanScope(boolean isAutoCreated) {
        this.isAutoCreated = isAutoCreated;
    }

    public boolean isAutoCreated() {
        return isAutoCreated;
    }
    public static BeanScope get(Class<?> from) {
        return (from.isAnnotationPresent(Scope.class)) ? ((Scope) from.getAnnotation(Scope.class)).value() : BeanScope.BUCKET;
    }
    public static BeanScope get(CtClass from) {
        try {
            return (from.hasAnnotation(Scope.class)) ? ((Scope) from.getAnnotation(Scope.class)).value() : BeanScope.BUCKET;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static BeanScope get(Object bean) {
        Class beanType = bean.getClass();
        try {
            Method scopeOverride = beanType.getDeclaredMethod("scope");
            if (scopeOverride.getReturnType() == BeanScope.class) {
                return (BeanScope) scopeOverride.invoke(bean);
            } else {
                return get(beanType);
            }
        } catch (Exception e) {
            return get(beanType);
        }
    }

    private static BeanScope get(String type) {
        return get(Couchbeans.getBeanType(type).orElseThrow());
    }
}
