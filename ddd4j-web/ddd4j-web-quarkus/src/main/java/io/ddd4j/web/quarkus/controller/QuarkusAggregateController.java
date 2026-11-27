package io.ddd4j.web.quarkus.controller;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.web.quarkus.TenantAwareResource;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.Serializable;

/**
 * Quarkus JAX-RS 聚合根资源模板（对标 Spring 版 {@code BaseAggregateController}）。
 *
 * <p>业务项目的聚合根 Resource 应继承此类，类型参数：
 * <ul>
 *   <li>{@code M}：聚合根领域模型（{@link AggregateRoot} 子类）</li>
 *   <li>{@code Q}：查询参数对象（需实现 {@link Serializable}，否则会被 JAX-RS 擦除为 {@code Object} 引发构建期 Arc NPE）</li>
 *   <li>{@code ID}：主键类型</li>
 * </ul>
 *
 * <p>标准路由：
 * <pre>
 *   GET    /                  - 分页列表
 *   GET    /{id}              - 详情
 *   POST   /                  - 新增
 *   PUT    /{id}              - 修改
 *   DELETE /{id}              - 删除
 *   POST   /{id}:disable      - 禁用（业务行为）
 *   POST   /{id}:enable       - 启用（业务行为）
 * </pre>
 *
 * @param <M>  聚合根模型
 * @param <Q>  查询参数
 * @param <ID> 主键
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public abstract class QuarkusAggregateController<M extends AggregateRoot<?>, Q extends Serializable, ID extends Serializable> extends TenantAwareResource {

    /**
     * \u5206\u9875\u67e5\u8be2
     *
     * @param pageNum \u9875\u7801
     * @param pageSize \u6bcf\u9875\u8bb0\u5f55\u6570
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u5206\u9875\u67e5\u8be2\u7ed3\u679c
     */
    @GET
    public R<Page<M>> page(@QueryParam("pageNum") @DefaultValue("1") int pageNum,
                           @QueryParam("pageSize") @DefaultValue("20") int pageSize,
                           @BeanParam Q query) {
        return R.ok(listPage(pageNum, pageSize, query));
    }

    /**
     * \u8be6\u60c5
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     * @return \u805a\u5408\u6839\u8be6\u60c5
     */
    @GET
    @Path("/{id}")
    public R<M> getById(@PathParam("id") ID id) {
        return R.ok(detail(id));
    }

    /**
     * \u65b0\u589e
     *
     * @param model \u5f85\u4fdd\u5b58\u7684\u805a\u5408\u6839
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839
     */
    @POST
    public R<M> create(M model) {
        return R.ok(save(model));
    }

    /**
     * \u4fee\u6539
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     * @param model \u4fee\u6539\u540e\u7684\u805a\u5408\u6839
     * @return \u4fee\u6539\u540e\u7684\u805a\u5408\u6839
     */
    @PUT
    @Path("/{id}")
    public R<M> update(@PathParam("id") ID id, M model) {
        return R.ok(modify(id, model));
    }

    /**
     * \u5220\u9664
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     * @return \u64cd\u4f5c\u7ed3\u679c
     */
    @DELETE
    @Path("/{id}")
    public R<Void> delete(@PathParam("id") ID id) {
        remove(id);
        return R.ok();
    }

    /**
     * \u7981\u7528\uff08\u4e1a\u52a1\u884c\u4e3a\uff09
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     * @return \u64cd\u4f5c\u7ed3\u679c
     */
    @POST
    @Path("/{id}:disable")
    public R<Void> disable(@PathParam("id") ID id) {
        doDisable(id);
        return R.ok();
    }

    /**
     * \u542f\u7528\uff08\u4e1a\u52a1\u884c\u4e3a\uff09
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     * @return \u64cd\u4f5c\u7ed3\u679c
     */
    @POST
    @Path("/{id}:enable")
    public R<Void> enable(@PathParam("id") ID id) {
        doEnable(id);
        return R.ok();
    }

    // ========== 抽象方法：业务项目必须实现 ==========

    /**
     * \u5206\u9875\u67e5\u8be2\uff08\u4e1a\u52a1\u5b9e\u73b0\uff09
     *
     * @param pageNum \u9875\u7801
     * @param pageSize \u6bcf\u9875\u8bb0\u5f55\u6570
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u5206\u9875\u67e5\u8be2\u7ed3\u679c
     */
    protected abstract Page<M> listPage(int pageNum, int pageSize, Q query);

    /**
     * \u8be6\u60c5\u67e5\u8be2\uff08\u4e1a\u52a1\u5b9e\u73b0\uff09
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     * @return \u805a\u5408\u6839\u8be6\u60c5
     */
    protected abstract M detail(ID id);

    /**
     * \u65b0\u589e\uff08\u4e1a\u52a1\u5b9e\u73b0\uff09
     *
     * @param model \u5f85\u4fdd\u5b58\u7684\u805a\u5408\u6839
     * @return \u4fdd\u5b58\u540e\u7684\u805a\u5408\u6839
     */
    protected abstract M save(M model);

    /**
     * \u4fee\u6539\uff08\u4e1a\u52a1\u5b9e\u73b0\uff09
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     * @param model \u4fee\u6539\u540e\u7684\u805a\u5408\u6839
     * @return \u4fee\u6539\u540e\u7684\u805a\u5408\u6839
     */
    protected abstract M modify(ID id, M model);

    /**
     * \u5220\u9664\uff08\u4e1a\u52a1\u5b9e\u73b0\uff09
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     */
    protected abstract void remove(ID id);

    /**
     * \u7981\u7528\uff08\u4e1a\u52a1\u5b9e\u73b0\uff0c\u9ed8\u8ba4\u7a7a\u64cd\u4f5c\uff09
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     */
    protected void doDisable(ID id) {
        // 业务项目按需覆盖
    }

    /**
     * \u542f\u7528\uff08\u4e1a\u52a1\u5b9e\u73b0\uff0c\u9ed8\u8ba4\u7a7a\u64cd\u4f5c\uff09
     *
     * @param id \u805a\u5408\u6839\u4e3b\u952e
     */
    protected void doEnable(ID id) {
        // 业务项目按需覆盖
    }
}
