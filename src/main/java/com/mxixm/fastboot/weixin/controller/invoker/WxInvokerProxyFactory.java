/*
 * Copyright (c) 2016-2017, Guangshan (guangshan1992@qq.com) and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mxixm.fastboot.weixin.controller.invoker;

import com.mxixm.fastboot.weixin.config.WxProperties;
import com.mxixm.fastboot.weixin.controller.invoker.executor.WxApiExecutor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ReflectionUtils;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * FastBootWeixin WxInvokerProxyFactory
 *
 * @author Guangshan
 * @date 2017/8/11 21:01
 * @since 0.1.2
 */
public class WxInvokerProxyFactory<T> implements InitializingBean, MethodInterceptor, BeanClassLoaderAware, FactoryBean<T> {

    private static final Log logger = LogFactory.getLog(MethodHandles.lookup().lookupClass());
    // private WxApiInvoker wxInvokerTemplateMock = MvcUriComponentsBuilder.on(WxApiInvoker.class);

    private final WxApiTypeInfo wxApiTypeInfo;

    private final Map<Method, WxApiMethodInfo> methodCache = new HashMap<>();

    private ClassLoader beanClassLoader;

    /**
     * 被代理的接口
     */
    private Class<T> clazz;

    private T proxy;

    private final WxApiExecutor wxApiExecutor;

    /**
     * 理论上构造方法上不能做这么多事的，以后再优化
     */
    public WxInvokerProxyFactory(Class<T> clazz, WxProperties wxProperties, WxApiExecutor wxApiExecutor) {
        this.clazz = clazz;
        this.wxApiExecutor = wxApiExecutor;
        this.wxApiTypeInfo = new WxApiTypeInfo(clazz, wxProperties.getUrl().getHost());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        proxy = (T) new ProxyFactory(clazz, this).getProxy(getBeanClassLoader());
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    public ClassLoader getBeanClassLoader() {
        return beanClassLoader;
    }

    /**
     * 后续加上缓存，一定要加
     *
     * @param inv
     * @return dummy
     * @throws Throwable
     */
    @Override
    public Object invoke(MethodInvocation inv) throws Throwable {
        if (ReflectionUtils.isObjectMethod(inv.getMethod())) {
            if ("toString".equals(inv.getMethod().getName())) {
                return clazz.getName();
            }
            return ReflectionUtils.invokeMethod(inv.getMethod(), inv.getThis(), inv.getArguments());
        }
        WxApiMethodInfo wxApiMethodInfo = methodCache.get(inv.getMethod());
        if (wxApiMethodInfo == null) {
            wxApiMethodInfo = new WxApiMethodInfo(inv.getMethod(), wxApiTypeInfo);
            methodCache.put(inv.getMethod(), wxApiMethodInfo);
        }
        return wxApiExecutor.execute(wxApiMethodInfo, inv.getArguments());
    }

    @Override
    public T getObject() throws Exception {
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return WxApiInvoker.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }


    /**
     @Override public Object invoke(MethodInvocation inv) throws Throwable {
     if (ReflectionUtils.isObjectMethod(inv.getMethod())) {
     return ReflectionUtils.invokeMethod(inv.getMethod(), inv.getThis(), inv.getArguments());
     }
     // 本来想用高大上一点的mock调用来实现的，但是mock的原理是代理，final的类即String为返回值时不能被代理，故作罢
     // Object mockReturnValue = ReflectionUtils.invokeMethod(inv.getMethod(), wxInvokerTemplateMock, inv.getArguments());
     UriComponentsBuilder builder = MvcUriComponentsBuilder.fromMethod(baseBuilder, clazz, inv.getMethod(), inv.getArguments());
     System.out.println(builder.build().toString());
     return "hahaha";
     }

     @Override public Object invoke(MethodInvocation inv) throws Throwable {
     Object mockReturnValue = ReflectionUtils.invokeMethod(inv.getMethod(), wxInvokerTemplateMock, inv.getArguments());
     UriComponentsBuilder builder = MvcUriComponentsBuilder.fromMethodCall(baseBuilder, mockReturnValue);
     System.out.println(builder.build().toString());
     return "hahaha";
     }
     */
}
