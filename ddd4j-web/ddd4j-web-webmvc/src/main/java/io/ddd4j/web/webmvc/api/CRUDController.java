package io.ddd4j.web.webmvc.api;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.web.utils.ReflectKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Legacy typed CRUD controller backed by the new aggregate repository SPI.
 *
 * @param <M> aggregate root type
 * @param <Q> query type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @deprecated prefer business-specific aggregate controllers.
 */
@Deprecated
@Slf4j(topic = "### BASE-WEB : CRUDController ###")
@SuppressWarnings({"unchecked", "rawtypes"})
public class CRUDController<M extends AggregateRoot<?>, Q extends Query<?>> {

    protected Repository<M, Serializable> repository;

    private Repository<M, Serializable> getRepository() {
        if (Objects.isNull(this.repository)) {
            Class<M> modelClass = ReflectKit.getSuperClassGenericType(this.getClass(), 0);
            this.repository = (Repository<M, Serializable>) RepositoryRegistry.repository(modelClass);
        }
        if (Objects.isNull(this.repository)) {
            log.error("未找到实体仓库");
        }
        return repository;
    }

    @PostMapping("/page")
    public Page<M> page(@RequestBody Q query) {
        return query.page();
    }

    @GetMapping("/page")
    public Page<M> getPage(Q query) {
        return query.page();
    }

    @PostMapping("/list")
    public List<M> list(@RequestBody Q query) {
        return query.list();
    }

    @GetMapping("/list")
    public List<M> getList(Q query) {
        return query.list();
    }

    @GetMapping("/detail")
    public M detail(Q query) {
        return (M) query.first();
    }

    @GetMapping("/detail/{id}")
    public M detail(@PathVariable("id") String id) {
        return getRepository().findById(id).orElse(null);
    }

    @PostMapping({"/save", "/create"})
    public M save(@RequestBody M model) {
        return getRepository().save(model);
    }

    @PostMapping("/saveBatch")
    public void saveBatch(@RequestBody List<M> models) {
        if (Objects.isNull(models)) {
            return;
        }
        for (M model : models) {
            getRepository().save(model);
        }
    }

    @PostMapping({"/update", "/modify"})
    public void update(@RequestBody M model) {
        getRepository().save(model);
    }

    @PostMapping({"/delete/{id}", "/remove/{id}"})
    public void delete(@PathVariable("id") String id) {
        getRepository().deleteById(id);
    }
}
