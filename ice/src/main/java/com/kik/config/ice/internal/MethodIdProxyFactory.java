package com.kik.config.ice.internal;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.kik.config.ice.ConfigSystem;
import com.kik.config.ice.exception.ConfigException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * A Static factory to construct configuration interface proxy instances.  These proxy instances are used to identify
 * the most recent method called on them, to enable a "mockito-like" api for specifying configuration methods in compiled
 * code.  An example use is when defining static overrides (see {@link OverrideModule})
 */
@Slf4j
public class MethodIdProxyFactory
{
    @Inject(optional = true)
    public Set<ConfigDescriptor> configDescriptors;

    private static final ThreadLocal<MethodAndScope> lastIdentifiedMethodAndScope = new ThreadLocal();

    private static final ConcurrentMap<Class<?>, Object> proxyMap = Maps.newConcurrentMap();

    private MethodIdProxyFactory()
    {
    }

    /**
     * Produces a proxy impl of a configuration interface to identify methods called on it
     */
    public static <C> C getProxy(final Class<C> configInterface)
    {
        return getProxy(configInterface, Optional.empty());
    }

    /**
     * Produces a proxy impl of a configuration interface to identify methods called on it
     */
    public static <C> C getProxy(final Class<C> configInterface, final Optional<String> scopeNameOpt)
    {

        // TODO: include scope in proxyMap key, and allow it to be identified along with the method.

        final C methodIdProxy;
        if (proxyMap.containsKey(configInterface)) {
            methodIdProxy = (C) proxyMap.get(configInterface);
        }
        else {
            methodIdProxy = createMethodIdProxy(configInterface, scopeNameOpt);
            proxyMap.put(configInterface, methodIdProxy);
        }
        return methodIdProxy;
    }

    /**
     * Retrieves the most recently called method on any configuration impl proxy generated by this factory, along with
     * the scope of the proxy.
     * Note that the identified method is only returned once.  Subsequent calls return null until another proxy method
     * is called and identified.
     */
    public static MethodAndScope getLastIdentifiedMethodAndScope()
    {
        final MethodAndScope mas = lastIdentifiedMethodAndScope.get();
        lastIdentifiedMethodAndScope.set(null);
        return mas;
    }

    @Value
    public static class MethodAndScope
    {
        private Method method;
        private Optional<String> scopeOpt;
    }

    private static <C> C createMethodIdProxy(final Class<C> interfaceToProxy, final Optional<String> scopeNameOpt)
    {
        final List<ConfigDescriptor> configDescList = ConfigSystem.descriptorFactory.buildDescriptors(interfaceToProxy, scopeNameOpt);

        DynamicType.Builder<C> typeBuilder = new ByteBuddy().subclass(interfaceToProxy);
        for (ConfigDescriptor desc : configDescList) {
            typeBuilder = typeBuilder.method(ElementMatchers.is(desc.getMethod())).intercept(InvocationHandlerAdapter.of((Object proxy, Method method1, Object[] args) -> {
                log.trace("BB InvocationHandler identifying method {} proxy {}, argCount {}", method1.getName(), proxy.toString(), args.length);
                lastIdentifiedMethodAndScope.set(new MethodAndScope(method1, scopeNameOpt));
                return defaultForType(desc.getMethod().getReturnType());
            }));
        }

        Class<? extends C> configImpl = typeBuilder.make()
            .load(interfaceToProxy.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
            .getLoaded();
        try {
            return configImpl.newInstance();
        }
        catch (InstantiationException | IllegalAccessException ex) {
            throw new ConfigException("Failed to instantiate identification implementation of Config {} scope {}",
                interfaceToProxy.getName(), scopeNameOpt.orElse("<empty>"), ex);
        }
    }

    /**
     * Provide the default value for any given type.
     *
     * @param type Type to get the default for
     * @return default value - primitive value or null depending on the type.
     */
    private static Object defaultForType(Class<?> type)
    {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(long.class)) {
            return (long) 0L;
        }
        if (type.equals(int.class)) {
            return (int) 0;
        }
        if (type.equals(short.class)) {
            return (short) 0;
        }
        if (type.equals(byte.class)) {
            return (byte) 0;
        }
        if (type.equals(char.class)) {
            return (char) '\u0000';
        }
        if (type.equals(float.class)) {
            return (float) 0.0f;
        }
        if (type.equals(double.class)) {
            return (double) 0.0d;
        }
        // should never happen
        throw new IllegalStateException("Unknown primitive type: " + type.getName());
    }
}