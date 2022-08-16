package com.couchbeans.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;

public class CouchbaseBeanFactory implements BeanFactory {

    private final Cluster cluster;
    private final Bucket bucket;
    private final Scope scope;
    private final Collection collection;

    public CouchbaseBeanFactory() {
        this(
                System.getProperty("com.couchbeans.cluster"),
                System.getProperty("com.couchbeans.username"),
                System.getProperty("com.couchbeans.password"),
                System.getProperty("com.couchbeans.bucket"),
                System.getProperty("com.couchbeans.scope"),
                System.getProperty("com.couchbeans.bean_collection")
        );
    }

    public CouchbaseBeanFactory(String address, String username, String password, String bucket, String scope, String collection) {
        this(Cluster.connect(address, username, password), bucket, scope, collection);
    }

    public CouchbaseBeanFactory(Cluster cluster, String bucket, String scope, String collection) {
        this(cluster, cluster.bucket(bucket), scope, collection);
    }

    public CouchbaseBeanFactory(Cluster cluster, Bucket bucket, String scope, String collection) {
        this(cluster, bucket, bucket.scope(scope), collection);
    }

    public CouchbaseBeanFactory(Cluster cluster, Bucket bucket, Scope scope, String collection) {
        this(cluster, bucket, scope, scope.collection(collection));
    }

    public CouchbaseBeanFactory(Cluster cluster, Bucket bucket, Scope scope, Collection collection) {
        this.cluster = cluster;
        this.bucket = bucket;
        this.scope = scope;
        this.collection = collection;
    }

    @Override
    public Object getBean(String name) throws BeansException {
      return getBean(name, new Object[0]);
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        Object bean = getBean(name);
        if (!requiredType.isAssignableFrom(bean.getClass())) {
          throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }

        return (T) bean;
    }

    @Override
    public Object getBean(String name, Object... args) throws BeansException {
        return null;
    }

    @Override
    public <T> T getBean(Class<T> requiredType) throws BeansException {
        return null;
    }

    @Override
    public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
        String name = requiredType.getCanonicalName();
      GetResult getResult = collection.get(name);
      if (getResult == null) {
        throw new NoSuchBeanDefinitionException(name);
      }
      JsonObject beanValues = getResult.contentAsObject();
      String beanClassName = beanValues.getString("__class");
      Boolean shared = beanValues.getBoolean("__is_shared");
      try {
          Class<?> beanClass = Class.forName(beanClassName);
          if (shared != null && shared) {
              return (T) getResult.contentAs(beanClass);
          } else {
            return (T) beanClass.newInstance();
          }
        } catch (Exception e) {
          throw new RuntimeException("Failed to load bean " + name, e);
        }
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        return null;
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        return null;
    }

    @Override
    public boolean containsBean(String name) {
        return false;
    }

    @Override
    public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        return false;
    }

    @Override
    public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
        return false;
    }

    @Override
    public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        return false;
    }

    @Override
    public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
        return false;
    }

    @Override
    public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        return null;
    }

    @Override
    public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        return null;
    }

    @Override
    public String[] getAliases(String name) {
        return new String[0];
    }
}
