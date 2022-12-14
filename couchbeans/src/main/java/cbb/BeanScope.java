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

    public static BeanScope forType(Class<?> from) {
        return (from.isAnnotationPresent(Scope.class)) ? ((Scope) from.getAnnotation(Scope.class)).value() :
                from.getCanonicalName().contains("$") ? BeanScope.MEMORY : BeanScope.BUCKET;
    }

    public static BeanScope forType(CtClass from) {
        try {
            return (from.hasAnnotation(Scope.class)) ? ((Scope) from.getAnnotation(Scope.class)).value() :
                    from.getName().contains("$") ? BeanScope.MEMORY : BeanScope.BUCKET;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static BeanScope forType(String type) throws ClassNotFoundException {
        return forType(Couchbeans.getBeanType(type));
    }

    public static BeanScope get(Object bean) {
        Class beanType = bean.getClass();
        try {
            Method scopeOverride = beanType.getDeclaredMethod("scope");
            if (scopeOverride.getReturnType() == BeanScope.class) {
                return (BeanScope) scopeOverride.invoke(bean);
            } else {
                return forType(beanType);
            }
        } catch (Exception e) {
            return forType(beanType);
        }
    }

}
