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

//Cglib动态代理，实现MethodInterceptor接口
/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
public class CglibProxy implements MethodInterceptor {

    private Object target;//需要代理的目标对象

    public CglibProxy(Object target) {
        this.target = target;
    }

    // 定义获取代理对象方法
    /**
     * 获取值。
     * @param objectTarget 对象参数。
     * @return 结果对象。
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

    // 定义获取代理对象方法
    /**
     * 获取值。
     * @param <T> 类型参数。
     * @param className 类型字面量。
     * @return 结果值。
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

    //重写拦截方法
    /**
     * intercept() 方法。
     * @param obj 对象参数。
     * @param method 被调用的方法。
     * @param arr 对象参数。
     * @param proxy 代理实例。
     * @return 结果对象。
     * @throws Throwable 调用过程中出现异常时抛出。
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] arr, MethodProxy proxy) throws Throwable {
        System.out.println("Cglib动态代理，监听开始！");
        Object invoke = method.invoke(target, arr);//方法执行，参数：target 目标对象 arr参数数组
        System.out.println("Cglib动态代理，监听结束！");
        return invoke;
    }

}
