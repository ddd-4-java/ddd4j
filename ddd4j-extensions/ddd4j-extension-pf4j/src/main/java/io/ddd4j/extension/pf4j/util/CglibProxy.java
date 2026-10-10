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
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * CGLIB 动态代理（基于 {@link MethodInterceptor}）。
 *
 * <p>拦截目标对象的方法调用并打印监听日志，用于插件调用链路的演示与调试。
 *
 * @since 2.0.x
 */
public class CglibProxy implements MethodInterceptor {

    private Object target;//需要代理的目标对象

    /**
     * 构造代理拦截器。
     *
     * @param target 需要代理的目标对象
     */
    public CglibProxy(Object target) {
        this.target = target;
    }

    /**
     * 拦截目标对象的方法调用，执行前后打印监听日志。
     *
     * @param obj    代理对象
     * @param method 被调用的方法
     * @param arr    方法参数数组
     * @param proxy  方法代理，用于调用父类实现
     * @return 方法调用的返回值
     * @throws Throwable 反射调用或代理逻辑抛出的异常
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] arr, MethodProxy proxy) throws Throwable {
        System.out.println("Cglib动态代理，监听开始！");
        Object invoke = method.invoke(target, arr);//方法执行，参数：target 目标对象 arr参数数组
        System.out.println("Cglib动态代理，监听结束！");
        return invoke;
    }

    /**
     * 为目标实例创建 CGLIB 代理对象。
     *
     * @param objectTarget 目标实例
     * @return CGLIB 代理对象
     */
    public static Object getCglibProxy(Object objectTarget) {
        //为目标对象target赋值
        CglibProxy target = new CglibProxy(objectTarget);
        Enhancer enhancer = new Enhancer();
        //设置父类,因为Cglib是针对指定的类生成一个子类，所以需要指定父类
        enhancer.setSuperclass(objectTarget.getClass());
        enhancer.setCallback(target);// 设置回调
        Object result = enhancer.create();//创建并返回代理对象
        return result;
    }

    /**
     * 按目标类型实例化并创建 CGLIB 代理对象。
     *
     * @param <T>         目标类型
     * @param className 目标类型
     * @return 目标类型的 CGLIB 代理对象
     */
    public static <T> T getCglibProxy(Class<T> className) {
        CglibProxy target = new CglibProxy(BeanUtils.instantiateClass(className));
        Enhancer enhancer = new Enhancer();
        //设置父类,因为Cglib是针对指定的类生成一个子类，所以需要指定父类
        enhancer.setSuperclass(className);
        enhancer.setCallback(target);// 设置回调
        Object result = enhancer.create();//创建并返回代理对象
        return (T) result;
    }

}
