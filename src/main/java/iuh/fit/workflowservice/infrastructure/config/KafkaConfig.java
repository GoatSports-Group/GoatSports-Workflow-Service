package iuh.fit.workflowservice.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.topic.file-move-completed}")
    private String fileMoveCompletedTopic;

    @Bean
    public NewTopic fileMoveCompletedTopic() {
        return TopicBuilder.name(fileMoveCompletedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
