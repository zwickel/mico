/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.github.ust.mico.core.resource;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.ConfigResource.Type;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ust.mico.core.broker.TopicBroker;
import io.github.ust.mico.core.configuration.KafkaConfig;
import io.github.ust.mico.core.dto.response.TopicDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = TopicResource.TOPIC_BASE_PATH, produces = MediaTypes.HAL_JSON_VALUE)
public class TopicResource {

    public static final String TOPIC_BASE_PATH = "/topics";

    @Autowired
    private TopicBroker topicBroker;

    @Autowired
    private KafkaConfig kafkaConfig;

    @GetMapping()
    public ResponseEntity<Resources<Resource<TopicDTO>>> getAllTopics() {
        log.debug("Request to get all topics");
        List<String> topics = topicBroker.getAllTopics();
        log.debug("Request got '{}' topics", topics.size());
        List<Resource<TopicDTO>> resourceList = new LinkedList<>();
        topics.forEach(topic -> resourceList.add(new Resource<>(new TopicDTO().setName(topic))));
        Resources<Resource<TopicDTO>> responseResourcesList = new Resources<>(resourceList);
        responseResourcesList.add(linkTo(methodOn(TopicResource.class).getAllTopics()).withSelfRel());
        return ResponseEntity.ok(responseResourcesList);
    }

    @GetMapping(path = "/purge/{topicName}")
    public ResponseEntity<String> purgeTopic(@PathVariable("topicName") String topicName) {
        log.debug("Request to purge topic '{}'", topicName);

        Properties props = new Properties();
        // log.info("Kafka config bootstrap servers: '{}'",
        // kafkaConfig.getBootstrapServers());
        // props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
        // kafkaConfig.getBootstrapServers());
        log.info("Kafka config bootstrap servers: '{}'", "kafka-0.broker.kafka.svc.cluster.local:9092");
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-0.broker.kafka.svc.cluster.local:9092");

        try (AdminClient admin = AdminClient.create(props)) {
            // Create config resource (here: topic with given name).
            ConfigResource topicConfigResource = new ConfigResource(Type.TOPIC, topicName);

            // Create config entry for topic which contains value to change.
            ConfigEntry retentionConfigEntry = new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, "1000");
            Config config = new Config(Collections.singleton(retentionConfigEntry));

            Map<ConfigResource, Config> topicAlterConfigs = new HashMap<>();
            topicAlterConfigs.put(topicConfigResource, config);

            // log.info("created topic alter config");
            AlterConfigsResult alterConfigsResult = admin.alterConfigs(topicAlterConfigs);
            log.info("topic config altered");

            // Wait for the config to be set.
            KafkaFuture<Void> kafkaFuture = alterConfigsResult.all();
            while (!kafkaFuture.isDone()) {
                log.info("waiting for completion");
                Thread.sleep(500);
            }

            kafkaFuture.get();

            // Check set config for topic.
            // DescribeConfigsResult describeConfigsResult = admin
            // .describeConfigs(Collections.singleton(topicConfigResource));
            // Map<ConfigResource, Config> descriptions = describeConfigsResult.all().get();
            // descriptions.get(topicConfigResource).entries().forEach(entry -> {
            // if (entry.name().equalsIgnoreCase(TopicConfig.RETENTION_MS_CONFIG)) {
            // log.info("topic retention.ms: '{}'", entry.value());
            // }
            // });

            // Give kafka some time, so the newly retention time setting can take effect.
            Thread.sleep(60000);

            // Then reset the retention time back to its standard value (remove the just set
            // configuration).
            retentionConfigEntry = new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, "-1");
            config = new Config(Collections.singleton(retentionConfigEntry));

            topicAlterConfigs.replace(topicConfigResource, config);

            alterConfigsResult = admin.alterConfigs(topicAlterConfigs);
            // Wait for the config to be set.
            alterConfigsResult.all().get();
        } catch (Exception e) {
            // TODO: handle exception
        }

        return ResponseEntity.ok("{\"purged\":\"true\"}");
    }

}
