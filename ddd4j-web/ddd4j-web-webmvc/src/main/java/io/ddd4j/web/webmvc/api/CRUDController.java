package io.ddd4j.web.webmvc.api;

import java.util.Objects;

import io.ddd4j.core.contract.BaseRepository;
import io.ddd4j.core.contract.Model;
import io.ddd4j.core.contract.Page;
import io.ddd4j.core.contract.Query;
import io.ddd4j.web.utils.ReflectKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Deprecated
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-WEB : CRUDController ###")
public class CRUDController<M extends Model, Q extends Query> {
    protected BaseRepository<M, Q> repository;

    private BaseRepository<M, Q> getRepository() {
        if (Objects.isNull(this.repository)) {
            Class<M> modelClass = ReflectKit.getSuperClassGenericType(this.getClass(), 0);
            this.repository = BaseRepository.of(modelClass);
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
        return query.first();
    }

    @GetMapping("/detail/{id}")
    public M detail(@PathVariable("id") String id) {
        return getRepository().get(id);
    }

    @PostMapping({"/save", "/create"})
    public M save(@RequestBody M model) {
        model.save();
        return model;
    }

    @PostMapping("/saveBatch")
    public void saveBatch(@RequestBody List<M> models) {
        getRepository().save(models);
    }

    @PostMapping({"/update", "/modify"})
    public void update(@RequestBody M model) {
        model.update();
    }

    @PostMapping({"/delete/{id}", "/remove/{id}"})
    public void delete(@PathVariable("id") String id) {
        getRepository().delete(id);
    }
}
