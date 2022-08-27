package cbb.annotations;

public @interface TargetNodes {
    String[] value() default {};
    boolean errorIfNoSuchTarget() default true;
}
