/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.service;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.github.dozermapper.core.Mapper;
import io.ddd4j.core.entity.PaginationEntity;
import io.ddd4j.core.mybatis.mapper.BaseMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.biz.web.servlet.support.RequestContextUtils;
import org.springframework.cache.CacheManager;
import org.springframework.context.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringValueResolver;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.util.List;

/**
 * 通用 Service 实现（MyBatis Plus 轨道）。
 *
 * @param <M> {@link BaseMapper} 实现
 * @param <T> {@link IBaseService} 持有的实体对象
 * @author <a href="https://github.com/wandl">wandl</a>
 * @deprecated 自 3.4.x 起，ddd4j-boot 重构为纯 DDD 脚手架。本类继承 MyBatis Plus 的
 * {@link ServiceImpl}，耦合 ORM 框架。
 * <p>
 * <b>替代方案</b>：在基础设施层实现 {@link io.ddd4j.core.domain.repository.Repository}，
 * 用 MyBatis Plus 的 Mapper 作为内部实现细节，不暴露到领域层。
 * <p>
 * 本类将在 5.0.x 版本移除。
 */
@Deprecated(since = "3.4.x", forRemoval = true)
@SuppressWarnings("removal")
public class BaseServiceImpl<M extends BaseMapper<T>, T extends Model<?>> extends ServiceImpl<M, T>
        implements InitializingBean, ApplicationEventPublisherAware, ApplicationContextAware,
        EmbeddedValueResolverAware, IBaseService<T> {

    @Autowired(required = false)
    @Getter
    protected NestedMessageSource messageSource;

    @Autowired(required = false)
    @Getter
    protected CacheManager cacheManager;

    @Autowired(required = false)
    @Getter
    protected Mapper beanMapper;

    @Getter
    private StringValueResolver valueResolver;

    @Getter
    private ApplicationEventPublisher eventPublisher;

    @Getter
    private ApplicationContext context;

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    protected String getMessage(String key, Object... args) {
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        return getMessageSource().getMessage(key, args, RequestContextUtils.getLocale(request));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setStatus(Serializable id, Serializable status) {
        return SqlHelper.retBool(getBaseMapper().setStatus(id, status));
    }

    @Override
    public Page<T> getPagedList(PaginationEntity<T> entity) {
        Page<T> page = new Page<>(entity.getPageNo(), entity.getLimit());
        if (!CollectionUtils.isEmpty(entity.getOrders())) {
            for (OrderItem orderBy : entity.getOrders()) {
                page.addOrder(orderBy);
            }
        }
        List<T> records = getBaseMapper().getPagedList(page, entity);
        page.setRecords(records);
        return page;
    }

    @Override
    public Page<T> getPagedList(Page<T> page, PaginationEntity<T> entity) {
        List<T> records = getBaseMapper().getPagedList(page, entity);
        page.setRecords(records);
        return page;
    }

    @Override
    public Long getCountByUid(Serializable uid) {
        return getBaseMapper().getCountByUid(uid);
    }

    @Override
    public Long getCountByCode(String code, Object origin) {
        return getBaseMapper().getCountByCode(code, origin);
    }

    @Override
    public Long getCountByName(String name, Object origin) {
        return getBaseMapper().getCountByName(name, origin);
    }

    @Override
    public Long getCountByParent(Object parent) {
        return getBaseMapper().getCountByParent(parent);
    }

    @Override
    public String getValue(String key) {
        return getBaseMapper().getValue(key);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.valueResolver = resolver;
    }

}
