package io.ddd4j.core.event;


import java.util.Properties;

@SuppressWarnings("serial")
public class PropsUpdateEvent extends io.ddd4j.core.contract.DomainEvent<Properties> {

    public PropsUpdateEvent(Object source, Properties props) {
        super(source, (Object) props);
    }

}
