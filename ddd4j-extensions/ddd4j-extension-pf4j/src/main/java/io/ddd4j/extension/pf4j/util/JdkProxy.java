/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.extension.pf4j.util;

import org.springframework.beans.BeanUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * JDK 动态代理（基于 {@link InvocationHandler}）。
 *
 * <p>拦截目标对象的方法调用并打印监听日志，用于插件调用链路的演示与调试。
 *
 * @since 2.0.x
 */
public class JdkProxy implements InvocationHandler {

    private Object target;//需要代理的目标对象

    /**
     * 构造调用处理器。
     *
     * @param target 需要代理的目标对象
     */
    public JdkProxy(Object target) {
        this.target = target;
    }

    /**
     * 拦截代理对象的方法调用，执行前后打印监听日志。
     *
     * @param proxy  代理对象
     * @param method 被调用的方法
     * @param args   方法参数数组
     * @return 方法调用的返回值
     * @throws Throwable 反射调用或代理逻辑抛出的异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("JDK动态代理，监听开始！");
        Object result = method.invoke(target, args);
        System.out.println("JDK动态代理，监听结束！");
        return result;
    }


    /**
     * 为目标实例创建 JDK 动态代理对象（要求目标实现接口）。
     *
     * @param targetObject 目标实例
     * @return JDK 动态代理对象
     */
    public static Object getJDKProxy(Object targetObject) {
        //JDK动态代理只能针对实现了接口的类进行代理，newProxyInstance 函数所需参数就可看出
        return Proxy.newProxyInstance(targetObject.getClass().getClassLoader(), targetObject.getClass().getInterfaces(), new JdkProxy(targetObject));
    }

    /**
     * 按目标类型实例化并创建 JDK 动态代理对象（要求目标类型实现接口）。
     *
     * @param <T>         目标类型
     * @param className 目标类型
     * @return 目标类型的 JDK 动态代理对象
     */
    public static <T> T getJDKProxy(Class<T> className) {
        JdkProxy target = new JdkProxy(BeanUtils.instantiateClass(className));
        //JDK动态代理只能针对实现了接口的类进行代理，newProxyInstance 函数所需参数就可看出
        return (T) Proxy.newProxyInstance(className.getClassLoader(), className.getInterfaces(), target);
    }

}
