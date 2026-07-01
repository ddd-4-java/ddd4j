package io.ddd4j.core.contract;

import io.ddd4j.core.contract.exception.ServiceException;
import io.ddd4j.core.util.MappingKit;
import io.ddd4j.kit.lang.JsonKit;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基础模型，支持增删改的充血模型
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Model implements Serializable {

    public static <M extends Model> boolean save(List<M> models) {
        if (Objects.isNull(models) || models.isEmpty()) {
            return false;
        }
        return BaseRepository.of(models.get(0).getClass()).save(models);
    }

    public static <M extends Model> boolean update(List<M> models) {
        if (Objects.isNull(models) || models.isEmpty()) {
            return false;
        }
        return BaseRepository.of(models.get(0).getClass()).update(models);
    }

    public static <M extends Model> boolean updateByKey(List<M> models) {
        if (Objects.isNull(models) || models.isEmpty()) {
            return false;
        }
        return BaseRepository.of(models.get(0).getClass()).updateByKey(models);
    }

    public static <Q extends Query> boolean delete(Q query) {
        return BaseRepository.of(query.getClass()).delete(query);
    }

    // 通过模型名匹配模型类
    public static Class<Model> ofName(String name) {
        Class<Model> modelClass = MappingKit.get("MODEL_NAME", name);
        if (Objects.isNull(modelClass)) {
            throw new ServiceException("Model: {} not found", name);
        }
        return modelClass;
    }

    // 通过Map参数转换为模型
    public static Model convert(String name, Map<String, Object> modelMap) {
        String json = JsonKit.toJson(modelMap);
        if (Objects.isNull(json) || io.ddd4j.kit.lang.StrKit.isEmpty(json)) {
            return null;
        }
        return JsonKit.toObject(json, Model.ofName(name));
    }

    // 批量通过Map参数转换为模型
    public static List<Model> convert(String name, List<Map<String, Object>> modelMaps) {
        if (Objects.nonNull(modelMaps) && !modelMaps.isEmpty()) {
            return modelMaps.stream().map(modelMap -> convert(name, modelMap)).filter(Objects::nonNull).collect(Collectors.toList());
        }
        return null;
    }

    public boolean save() {
        return BaseRepository.of(this.getClass()).save(this);
    }

    public boolean update() {
        return BaseRepository.of(this.getClass()).update(this);
    }

    public boolean saveOrUpdate() {
        return BaseRepository.of(this.getClass()).saveOrUpdate(this);
    }

    public <Q extends Query> boolean update(Q query) {
        query.with();
        query.setOrderBys(null);
        return BaseRepository.of(query.getClass()).update(this, query);
    }

    public boolean updateByKey() {
        return BaseRepository.of(this.getClass()).updateByKey(this);
    }

    public <Q extends Query> void fill(Q query) {
        BaseRepository.of(this.getClass()).fill(query, this);
    }
}