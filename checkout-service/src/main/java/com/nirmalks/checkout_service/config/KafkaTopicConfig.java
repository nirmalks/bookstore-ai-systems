package com.nirmalks.checkout_service.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicConfig {

	public static final String TOPIC_ORDER_COMPLETED = "order-completed";

	@Value("${spring.kafka.bootstrap-servers:localhost:9094}")
	private String bootstrapServers;

	@Value("${kafka.topics.order-completed.partitions:3}")
	private int partitions;

	@Value("${kafka.topics.order-completed.replication-factor:1}")
	private short replicationFactor;

	@Value("${kafka.topics.order-completed.min-in-sync-replicas:1}")
	private String minInSyncReplicas;

	@Value("${kafka.topics.order-completed.retention-ms:604800000}")
	private String retentionMs;

	@Bean
	public KafkaAdmin kafkaAdmin() {
		Map<String, Object> configs = new HashMap<>();
		configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		return new KafkaAdmin(configs);
	}

	@Bean
	public NewTopic orderCompletedTopic() {
		return TopicBuilder.name(TOPIC_ORDER_COMPLETED)
				.partitions(partitions)
				.replicas(replicationFactor)
				.config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, minInSyncReplicas)
				.config(TopicConfig.RETENTION_MS_CONFIG, retentionMs)
				.config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
				.build();
	}
}
