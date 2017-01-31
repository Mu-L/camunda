package org.camunda.optimize.service.util;

import org.camunda.optimize.service.util.schema.ElasticSearchSchemaManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

public class ElasticSearchSchemaInitializer {

  @Autowired
  ElasticSearchSchemaManager schemaManager;

  @PostConstruct
  public void initializeSchema() {
    if (!schemaManager.schemaAlreadyExists()) {
      schemaManager.createOptimizeIndex();
      schemaManager.createMappings();
    }
  }

}
