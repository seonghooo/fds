package com.seongho.fds.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;

@Configuration
public class KafkaRetryConfig {

    @Bean
    public RetryTopicConfiguration transactionRetryTopic(KafkaTemplate<String, Object> kafkaTemplate) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(4)                          // 최초 1회 + 재시도 3회
                .fixedBackOff(1000)                      // 재시도 간격 1초
                .dltSuffix(".DLT")
                .includeTopic("transaction-topic")
                .create(kafkaTemplate);
    }
}
