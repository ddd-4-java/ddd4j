package io.ddd4j.mq.sqs.autoconfigure;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.sqs.consumer.SqsMQConsumerEndpointRegistrar;
import io.ddd4j.mq.sqs.publisher.SqsMQEventPublisher;
import io.ddd4j.mq.sqs.spi.SqsMQBrokerAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AWS SQS 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=sqs 时生效。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jSqsMQAutoConfiguration {

    /**
     * 注册 AmazonSQS 客户端（骨架 Bean，可通过自定义 AmazonSQS 覆盖）。
     *
     * @param region AWS 区域
     * @return SQS 客户端
     */
    @Bean
    public AmazonSQS amazonSqs(
            @Value("${ddd4j.mq.sqs.region:us-east-1}") String region) {
        return AmazonSQSClientBuilder.standard()
                .withRegion(region)
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();
    }

    /**
     * 注册 SQS 消费端点编排器。
     */
    @Bean(destroyMethod = "close")
    public SqsMQConsumerEndpointRegistrar sqsMQConsumerEndpointRegistrar(
            ObjectProvider<AmazonSQS> amazonSqsProvider,
            @Value("${ddd4j.mq.sqs.queue-url:}") String queueUrl,
            Ddd4jMQProperties properties) {
        return new SqsMQConsumerEndpointRegistrar(amazonSqsProvider.getIfAvailable(), queueUrl, properties);
    }

    /**
     * 注册 SQS Broker 适配器。
     */
    @Bean
    public SqsMQBrokerAdapter sqsMQBrokerAdapter(
            ObjectProvider<AmazonSQS> amazonSqsProvider,
            @Value("${ddd4j.mq.sqs.queue-url:}") String queueUrl,
            Ddd4jMQProperties properties,
            SqsMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new SqsMQBrokerAdapter(amazonSqsProvider.getIfAvailable(), queueUrl, properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    public MQEventPublisher sqsMQEventPublisher(
            ObjectProvider<AmazonSQS> amazonSqsProvider,
            @Value("${ddd4j.mq.sqs.queue-url:}") String queueUrl,
            Ddd4jMQProperties properties) {
        return new SqsMQEventPublisher(amazonSqsProvider.getIfAvailable(), queueUrl, properties);
    }
}
