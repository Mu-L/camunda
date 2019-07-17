/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;


public abstract class AbstractUpgradeIT {

  public static final MetadataType METADATA_TYPE = new MetadataType();

  protected ObjectMapper objectMapper;
  protected OptimizeElasticsearchClient prefixAwareClient;
  protected ElasticsearchMetadataService metadataService;
  protected ConfigurationService configurationService;
  protected OptimizeIndexNameService indexNameService;

  @Before
  protected void setUp() throws Exception {
    configurationService = new ConfigurationService();
    if (objectMapper == null) {
      objectMapper = new ObjectMapperFactory(
        new OptimizeDateTimeFormatterFactory().getObject(),
        configurationService
      ).createOptimizeMapper();
    }
    if (prefixAwareClient == null) {
      indexNameService = new OptimizeIndexNameService(configurationService);
      prefixAwareClient = new OptimizeElasticsearchClient(
        ElasticsearchHighLevelRestClientBuilder.build(configurationService),
        indexNameService
      );
    }
    if (metadataService == null) {
      metadataService = new ElasticsearchMetadataService(objectMapper);
    }
    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
  }

  @After
  public void after() throws Exception {
    cleanAllDataFromElasticsearch();
    deleteEnvConfig();
  }

  protected void initSchema(List<TypeMappingCreator> mappingCreators) {
    final ElasticSearchSchemaManager elasticSearchSchemaManager = new ElasticSearchSchemaManager(
      metadataService, new ConfigurationService(), indexNameService, mappingCreators, objectMapper
    );
    elasticSearchSchemaManager.initializeSchema(prefixAwareClient);
  }

  protected void setMetadataIndexVersion(String version) {
    metadataService.writeMetadata(prefixAwareClient, new MetadataDto(version));
  }

  private void cleanAllDataFromElasticsearch() {
    try {
      prefixAwareClient.getHighLevelClient().indices().delete(new DeleteIndexRequest("_all"), RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new RuntimeException("Failed cleaning elasticsearch");
    }
  }

}
