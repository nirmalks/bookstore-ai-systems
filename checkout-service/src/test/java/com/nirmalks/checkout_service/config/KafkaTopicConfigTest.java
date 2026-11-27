package com.nirmalks.checkout_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

	@Test
	@DisplayName("Should build order-completed NewTopic with 3 partitions and durability policies")
	void shouldBuildOrderCompletedTopic() {
		KafkaTopicConfig config = new KafkaTopicConfig();
		ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9094");
		ReflectionTestUtils.setField(config, "partitions", 3);
		ReflectionTestUtils.setField(config, "replicationFactor", (short) 1);
		ReflectionTestUtils.setField(config, "minInSyncReplicas", "1");
		ReflectionTestUtils.setField(config, "retentionMs", "604800000");

		NewTopic topic = config.orderCompletedTopic();

		assertThat(topic.name()).isEqualTo("order-completed");
		assertThat(topic.numPartitions()).isEqualTo(3);
		assertThat(topic.replicationFactor()).isEqualTo((short) 1);
		assertThat(topic.configs().get(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG)).isEqualTo("1");
		assertThat(topic.configs().get(TopicConfig.RETENTION_MS_CONFIG)).isEqualTo("604800000");
		assertThat(topic.configs().get(TopicConfig.CLEANUP_POLICY_CONFIG)).isEqualTo(TopicConfig.CLEANUP_POLICY_DELETE);

		KafkaAdmin admin = config.kafkaAdmin();
		assertThat(admin.getConfigurationProperties().get("bootstrap.servers")).isEqualTo("localhost:9094");
	}
}
