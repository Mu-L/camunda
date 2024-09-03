/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.NoopExporterConfiguration.ElasticsearchConfig;
import io.camunda.exporter.NoopExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;

public class ElasticsearchSchemaManager implements SchemaManager {
  private final SearchEngineClient elasticsearchClient;
  private final List<IndexDescriptor> indexDescriptors;
  private final List<IndexTemplateDescriptor> indexTemplateDescriptors;
  private final ElasticsearchConfig elasticsearchConfig;

  public ElasticsearchSchemaManager(
      final SearchEngineClient elasticsearchClient,
      final List<IndexDescriptor> indexDescriptors,
      final List<IndexTemplateDescriptor> indexTemplateDescriptors,
      final ElasticsearchConfig elasticsearchConfig) {
    this.elasticsearchClient = elasticsearchClient;
    this.indexDescriptors = indexDescriptors;
    this.indexTemplateDescriptors = indexTemplateDescriptors;
    this.elasticsearchConfig = elasticsearchConfig;
  }

  @Override
  public void initialiseResources() {
    indexTemplateDescriptors.forEach(this::createIndexTemplate);
    indexDescriptors.forEach(elasticsearchClient::createIndex);
  }

  private void createIndexTemplate(final IndexTemplateDescriptor templateDescriptor) {
    final var templateReplicas =
        elasticsearchConfig.replicasByIndexName.getOrDefault(
            templateDescriptor.getIndexName(),
            elasticsearchConfig.defaultSettings.numberOfReplicas);
    final var templateShards =
        elasticsearchConfig.shardsByIndexName.getOrDefault(
            templateDescriptor.getIndexName(), elasticsearchConfig.defaultSettings.numberOfShards);

    final var settings = new IndexSettings();
    settings.numberOfShards = templateShards;
    settings.numberOfReplicas = templateReplicas;

    elasticsearchClient.createIndexTemplate(templateDescriptor, settings);
  }
}
