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

//JDK动态代理实现InvocationHandler接口
/**
 * JDK \u52a8\u6001\u4ee3\u7406\uff08\u57fa\u4e8e {@link InvocationHandler}\uff09\u3002
 *
 *  <p>\u62e6\u622a\u76ee\u6807\u5bf9\u8c61\u7684\u65b9\u6cd5\u8c03\u7528\u5e76\u6253\u5370\u76d1\u542c\u65e5\u5fd7\uff0c\u7528\u4e8e\u63d2\u4ef6\u8c03\u7528\u94fe\u8def\u7684\u6f14\u793a\u4e0e\u8c03\u8bd5\u3002
 * @since 2.0.x
 */
public class JdkProxy implements InvocationHandler {

    /** \u8f6c\u53d1\u63a5\u53e3\u4ee3\u7406\u65b9\u6cd5\u8c03\u7528\u7684\u76ee\u6807\u5b9e\u4f8b\u3002 */
    private Object target;//需要代理的目标对象

    /**
     * \u6784\u9020\u8c03\u7528\u5904\u7406\u5668\u3002
     * @param target \u9700\u8981\u4ee3\u7406\u7684\u76ee\u6807\u5bf9\u8c61
     */
    public JdkProxy(Object target) {
        this.target = target;
    }

    //定义获取代理对象方法
    /**
     * \u4e3a\u76ee\u6807\u5b9e\u4f8b\u521b\u5efa JDK \u52a8\u6001\u4ee3\u7406\u5bf9\u8c61\uff08\u8981\u6c42\u76ee\u6807\u5b9e\u73b0\u63a5\u53e3\uff09\u3002
     * @param targetObject \u76ee\u6807\u5b9e\u4f8b
     * @return JDK \u52a8\u6001\u4ee3\u7406\u5bf9\u8c61
     */
    public static Object getJDKProxy(Object targetObject) {
        //JDK动态代理只能针对实现了接口的类进行代理，newProxyInstance 函数所需参数就可看出
        return Proxy.newProxyInstance(targetObject.getClass().getClassLoader(), targetObject.getClass().getInterfaces(), new JdkProxy(targetObject));
    }

    // 定义获取代理对象方法
    /**
     * \u6309\u76ee\u6807\u7c7b\u578b\u5b9e\u4f8b\u5316\u5e76\u521b\u5efa JDK \u52a8\u6001\u4ee3\u7406\u5bf9\u8c61\uff08\u8981\u6c42\u76ee\u6807\u7c7b\u578b\u5b9e\u73b0\u63a5\u53e3\uff09\u3002
     * @param <T> \u76ee\u6807\u7c7b\u578b
     * @param className \u76ee\u6807\u7c7b\u578b
     * @return \u76ee\u6807\u7c7b\u578b\u7684 JDK \u52a8\u6001\u4ee3\u7406\u5bf9\u8c61
     */
    public static <T> T getJDKProxy(Class<T> className) {
        JdkProxy target = new JdkProxy(BeanUtils.instantiateClass(className));
        //JDK动态代理只能针对实现了接口的类进行代理，newProxyInstance 函数所需参数就可看出
        return (T) Proxy.newProxyInstance(className.getClassLoader(), className.getInterfaces(), target);
    }

    /**
     * \u62e6\u622a\u4ee3\u7406\u5bf9\u8c61\u7684\u65b9\u6cd5\u8c03\u7528\uff0c\u6267\u884c\u524d\u540e\u6253\u5370\u76d1\u542c\u65e5\u5fd7\u3002
     * @param proxy \u4ee3\u7406\u5bf9\u8c61
     * @param method \u88ab\u8c03\u7528\u7684\u65b9\u6cd5
     * @param args \u65b9\u6cd5\u53c2\u6570\u6570\u7ec4
     * @return \u65b9\u6cd5\u8c03\u7528\u7684\u8fd4\u56de\u503c
     * @throws Throwable \u53cd\u5c04\u8c03\u7528\u6216\u4ee3\u7406\u903b\u8f91\u629b\u51fa\u7684\u5f02\u5e38
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("JDK动态代理，监听开始！");
        Object result = method.invoke(target, args);
        System.out.println("JDK动态代理，监听结束！");
        return result;
    }

}
