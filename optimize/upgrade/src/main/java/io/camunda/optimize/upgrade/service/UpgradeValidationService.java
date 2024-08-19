/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.service;

import static io.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static io.camunda.optimize.service.util.DatabaseVersionChecker.checkESVersionSupport;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.slf4j.Logger;

public class UpgradeValidationService {

  private static final String ENVIRONMENT_CONFIG_FILE = "environment-config.yaml";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(UpgradeValidationService.class);

  public UpgradeValidationService() {}

  public void validateSchemaVersions(
      final String schemaVersion, final String fromVersion, final String toVersion) {
    if (schemaVersion == null) {
      throw new RuntimeException("Schema Version cannot be null");
    }
    if (fromVersion == null) {
      throw new RuntimeException("From version cannot be null");
    }
    if (toVersion == null) {
      throw new RuntimeException("To version cannot be null");
    }

    try {
      if (!(Objects.equals(fromVersion, schemaVersion)
          || Objects.equals(fromVersion, getMajorAndMinor(schemaVersion))
          || Objects.equals(toVersion, schemaVersion))) {
        throw new UpgradeRuntimeException(
            String.format(
                "Schema version saved in Metadata [%s] must be one of [%s, %s]",
                schemaVersion, fromVersion, toVersion));
      }
    } catch (final Exception e) {
      throw new UpgradeRuntimeException(e.getMessage(), e);
    }
  }

  public void validateESVersion(
      final OptimizeElasticsearchClient restClient, final String toVersion) {
    try {
      checkESVersionSupport(restClient.getHighLevelClient(), restClient.requestOptions());
    } catch (final Exception e) {
      final String errorMessage =
          "It was not possible to upgrade Optimize to version "
              + toVersion
              + ".\n"
              + e.getMessage();
      throw new UpgradeRuntimeException(errorMessage);
    }
  }

  public void validateEnvironmentConfigInClasspath() {
    boolean configAvailable = false;
    try (final InputStream resourceAsStream =
        UpgradeValidationService.class.getResourceAsStream("/" + ENVIRONMENT_CONFIG_FILE)) {
      if (resourceAsStream != null) {
        configAvailable = true;
      }
    } catch (final IOException e) {
      log.error("Can't resolve " + ENVIRONMENT_CONFIG_FILE, e);
    }

    if (!configAvailable) {
      throw new UpgradeRuntimeException(
          "Couldn't read " + ENVIRONMENT_CONFIG_FILE + " from config folder in Optimize root!");
    }
  }
}
