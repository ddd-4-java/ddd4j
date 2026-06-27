package io.ddd4j.web.spring.api;

import io.ddd4j.core.contract.BaseRepository;
import io.ddd4j.core.contract.Model;
import io.ddd4j.core.contract.Page;
import io.ddd4j.core.contract.Query;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聚合控制器，实现该控制器的Controller，自带CRUD方法
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public interface AggregateController {

    // 公共POST分页
    @PostMapping("/{model}/page")
    default Page<Model> postPage(@PathVariable("model") String model, @RequestBody Map<String, Object> body) {
        return Query.convert(model, body).page();
    }

    // 公共分页
    @GetMapping("/{model}/page")
    default Page<Model> getPage(@PathVariable("model") String model, @RequestParam Map<String, Object> params) {
        return Query.convert(model, params).page();
    }

    // 公共POST列表
    @PostMapping("/{model}/list")
    default List<Model> postList(@PathVariable("model") String model, @RequestBody Map<String, Object> body) {
        return Query.convert(model, body).list();
    }

    // 公共列表
    @GetMapping("/{model}/list")
    default List<Model> getList(@PathVariable("model") String model, @RequestParam Map<String, Object> params) {
        return Query.convert(model, params).list();
    }

    // 公共详情
    @GetMapping("/{model}/detail")
    default Model detail(@PathVariable("model") String model, @RequestParam Map<String, Object> params) {
        return Query.convert(model, params).first();
    }

    // 公共详情
    @GetMapping("/{model}/detail/{id}")
    default Model detail(@PathVariable("model") String model, @PathVariable("id") String id) {
        return BaseRepository.of(Model.ofName(model)).get(id);
    }

    // 公共是否存在
    @GetMapping("/{model}/exist")
    default Boolean exist(@PathVariable("model") String model, @RequestParam Map<String, Object> params) {
        return Query.convert(model, params).exist();
    }

    // 公共计数
    @GetMapping("/{model}/count")
    default Integer count(@PathVariable("model") String model, @RequestParam Map<String, Object> params) {
        return Query.convert(model, params).count();
    }

    // 公共创建
    @PostMapping("/{model}/create")
    default Model create(@PathVariable("model") String model, @RequestBody Map<String, Object> body) {
        Model m = Model.convert(model, body);
        if (m == null) return null;
        m.save();
        return m;
    }

    // 公共批量创建
    @PostMapping("/{model}/saveBatch")
    default void saveBatch(@PathVariable("model") String model, @RequestBody List<Map<String, Object>> body) {
        BaseRepository.of(Model.ofName(model)).save(Model.convert(model, body));
    }

    // 公共修改
    @PostMapping({"/{model}/update", "/{model}/modify"})
    default void update(@PathVariable("model") String model, @RequestBody Map<String, Object> body) {
        Model m = Model.convert(model, body);
        if (m != null) {
            m.update();
        }
    }

    // 公共批量修改
    @PostMapping({"/{model}/updateBatch", "/{model}/modifyBatch"})
    default void updateBatch(@PathVariable("model") String model, @RequestBody List<Map<String, Object>> body) {
        List<Model> models = Model.convert(model, body);
        for (Model m : models) {
            m.update();
        }
    }

    // 公共保存（创建或修改）
    @PostMapping("/{model}/save")
    default Model save(@PathVariable("model") String model, @RequestBody Map<String, Object> body) {
        Model m = Model.convert(model, body);
        if (m == null) return null;
        m.saveOrUpdate();
        return m;
    }

    // 公共删除
    @PostMapping({"/{model}/delete/{id}", "/{model}/remove/{id}"})
    default void delete(@PathVariable("model") String model, @PathVariable("id") String id) {
        BaseRepository.of(Model.ofName(model)).delete(id);
    }

    // 公共按条件删除
    @PostMapping("/{model}/remove")
    default void removeByQuery(@PathVariable("model") String model, @RequestBody Map<String, Object> body) {
        Query query = Query.convert(model, body);
        BaseRepository.of(Model.ofName(model)).delete(query);
    }

}