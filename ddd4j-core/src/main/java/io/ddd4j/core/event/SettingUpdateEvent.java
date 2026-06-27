package io.ddd4j.core.event;


import java.util.Map;

/**
 * 系统参数更新事件
 */
@SuppressWarnings("serial")
public class SettingUpdateEvent extends io.ddd4j.core.contract.DomainEvent<Map<String, String>> {

    public SettingUpdateEvent(Object source, Map<String, String> props) {
        super(source, (Object) props);
    }

}
