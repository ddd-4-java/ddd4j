package io.ddd4j.data.typehandlers;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

public class JSONArrayTypeHandler extends BaseTypeHandler<JSONArray> {

    @Override
    protected String convert(JSONArray obj) {
        return JSONUtil.toJsonStr(obj);
    }

    @Override
    protected JSONArray parse(String result) {
        return JSONUtil.parseArray(result);
    }

}