package io.ddd4j.data.mybatis.typehandlers;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
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