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
 * CGLIB \u52a8\u6001\u4ee3\u7406\uff08\u57fa\u4e8e {@link MethodInterceptor}\uff09\u3002
 *
 *  <p>\u62e6\u622a\u76ee\u6807\u5bf9\u8c61\u7684\u65b9\u6cd5\u8c03\u7528\u5e76\u6253\u5370\u76d1\u542c\u65e5\u5fd7\uff0c\u7528\u4e8e\u63d2\u4ef6\u8c03\u7528\u94fe\u8def\u7684\u6f14\u793a\u4e0e\u8c03\u8bd5\u3002
 * @since 2.0.x
 */
public class CglibProxy implements MethodInterceptor {

    /** \u62e6\u622a\u65b9\u6cd5\u8c03\u7528\u540e\u5b9e\u9645\u63a5\u6536\u8c03\u7528\u7684\u76ee\u6807\u5b9e\u4f8b\u3002 */
    private Object target;//需要代理的目标对象

    /**
     * \u6784\u9020\u4ee3\u7406\u62e6\u622a\u5668\u3002
     * @param target \u9700\u8981\u4ee3\u7406\u7684\u76ee\u6807\u5bf9\u8c61
     */
    public CglibProxy(Object target) {
        this.target = target;
    }

    // 定义获取代理对象方法
    /**
     * \u4e3a\u76ee\u6807\u5b9e\u4f8b\u521b\u5efa CGLIB \u4ee3\u7406\u5bf9\u8c61\u3002
     * @param objectTarget \u76ee\u6807\u5b9e\u4f8b
     * @return CGLIB \u4ee3\u7406\u5bf9\u8c61
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
     * \u6309\u76ee\u6807\u7c7b\u578b\u5b9e\u4f8b\u5316\u5e76\u521b\u5efa CGLIB \u4ee3\u7406\u5bf9\u8c61\u3002
     * @param <T> \u76ee\u6807\u7c7b\u578b
     * @param className \u76ee\u6807\u7c7b\u578b
     * @return \u76ee\u6807\u7c7b\u578b\u7684 CGLIB \u4ee3\u7406\u5bf9\u8c61
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
     * \u62e6\u622a\u76ee\u6807\u5bf9\u8c61\u7684\u65b9\u6cd5\u8c03\u7528\uff0c\u6267\u884c\u524d\u540e\u6253\u5370\u76d1\u542c\u65e5\u5fd7\u3002
     * @param obj \u4ee3\u7406\u5bf9\u8c61
     * @param method \u88ab\u8c03\u7528\u7684\u65b9\u6cd5
     * @param arr \u65b9\u6cd5\u53c2\u6570\u6570\u7ec4
     * @param proxy \u65b9\u6cd5\u4ee3\u7406\uff0c\u7528\u4e8e\u8c03\u7528\u7236\u7c7b\u5b9e\u73b0
     * @return \u65b9\u6cd5\u8c03\u7528\u7684\u8fd4\u56de\u503c
     * @throws Throwable \u53cd\u5c04\u8c03\u7528\u6216\u4ee3\u7406\u903b\u8f91\u629b\u51fa\u7684\u5f02\u5e38
     */
    @Override
    public Object intercept(Object obj, Method method, Object[] arr, MethodProxy proxy) throws Throwable {
        System.out.println("Cglib动态代理，监听开始！");
        Object invoke = method.invoke(target, arr);//方法执行，参数：target 目标对象 arr参数数组
        System.out.println("Cglib动态代理，监听结束！");
        return invoke;
    }

}
