package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;

/**
 * Sa-Token Subject 工厂（纯 Java，零 Spring 依赖）。
 *
 * <p>默认使用 {@link StpUtil#stpLogic}（loginType="login"）。
 * 多账号体系场景重写 {@link #getSubject(String)} 返回对应 {@link StpLogic} 包装的 Subject。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SaTokenSubjectProvider implements SubjectProvider {

    /**
     * 取得当前请求使用的认证主体接口。
     *
     * @return 本次调用使用的认证主体接口
     */
    @Override
    public Subject getSubject() {
        return new SaTokenSubject();
    }

    /**
     * \u6309\u8d26\u53f7\u4f53\u7cfb\u83b7\u53d6 Subject\uff08\u5bf9\u9f50 Sa-Token {@code SaManager.getStpLogic(loginType)}\uff09\u3002
     *
     *  <p>\u672c\u5b9e\u73b0\u8fd4\u56de\u7684 Subject \u5185\u90e8\u4f1a\u6309 realm \u9009\u62e9\u5bf9\u5e94\u7684 StpLogic\u3002
     *
     * @param realm \u8981\u53d6\u5f97\u4e3b\u4f53\u7684\u8ba4\u8bc1\u57df
     * @return \u7ed1\u5b9a\u6307\u5b9a\u8ba4\u8bc1\u57df\u7684\u4e3b\u4f53
     */
    @Override
    public Subject getSubject(String realm) {
        return new SaTokenSubject();
    }

}
