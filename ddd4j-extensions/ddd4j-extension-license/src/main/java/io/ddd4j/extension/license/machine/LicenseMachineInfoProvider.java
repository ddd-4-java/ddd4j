package io.ddd4j.extension.license.machine;

import java.util.Set;

/**
 * 当前运行环境机器信息提供策略。
 *
 * <p>业务可注入云平台实例 ID、容器节点标识或可信硬件序列号，替代默认系统采集逻辑。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface LicenseMachineInfoProvider {

    Set<String> ipAddresses();

    Set<String> macAddresses();

    String serialNumber();
}
