package io.ddd4j.boot.data.typehandlers;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

public class JSONObjectTypeHandler extends BaseTypeHandler<JSONObject> {

    @Override
    protected String convert(JSONObject obj) {
        return JSONUtil.toJsonStr(obj);
    }

    @Override
    protected JSONObject parse(String result) {
        return JSONUtil.parseObj(result).toBean(JSONObject.class);
    }

}