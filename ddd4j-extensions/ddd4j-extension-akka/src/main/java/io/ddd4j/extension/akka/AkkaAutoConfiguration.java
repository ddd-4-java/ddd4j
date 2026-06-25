package io.ddd4j.extension.akka;

import akka.actor.ActorSystem;
import io.ddd4j.extension.akka.actor.SpringExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Akka Auto Configuration
 */
@Configuration(proxyBeanMethods = false)
// @EnableConfigurationProperties(AkkaProperties.class)
public class AkkaAutoConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public ActorSystem actorSystem(AkkaProperties properties) {
        ActorSystem system = ActorSystem.create(properties.getName());
        ((SpringExtension.SpringExt) SpringExtension.SPRING_EXTENSION_PROVIDER.get(system)).initialize(this.applicationContext);
        return system;
    }

    public AkkaAutoConfiguration() {
    }

    public ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof AkkaAutoConfiguration)) {
            return false;
        } else {
            AkkaAutoConfiguration other = (AkkaAutoConfiguration) o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$applicationContext = this.getApplicationContext();
                Object other$applicationContext = other.getApplicationContext();
                if (this$applicationContext == null) {
                    if (other$applicationContext != null) {
                        return false;
                    }
                } else if (!this$applicationContext.equals(other$applicationContext)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof AkkaAutoConfiguration;
    }

    public int hashCode() {
        int result = 1;
        Object $applicationContext = this.getApplicationContext();
        result = result * 59 + ($applicationContext == null ? 43 : $applicationContext.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "AkkaAutoConfiguration(applicationContext=" + this.getApplicationContext() + ")";
    }

}
