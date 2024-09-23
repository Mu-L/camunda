/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.clustering;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.utils.Version;
import io.camunda.application.commons.actor.ActorSchedulerConfiguration.SchedulerConfiguration;
import io.camunda.zeebe.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class AtomixClusterConfiguration {

  private final ClusterConfig config;
  private final String prefix;

  @Autowired
  public AtomixClusterConfiguration(
      final ClusterConfig config, final SchedulerConfiguration schedulerConfiguration) { // TODO

    this.config = config;
    if (schedulerConfiguration != null) {
      prefix = schedulerConfiguration.prefix();
    } else {
      prefix = "";
    }
  }

  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster() {
    final var atomixCluster =
        new AtomixCluster(config, Version.from(VersionUtil.getVersion()), prefix);

    // Pass prefix to SWIM Membership Protocol
    atomixCluster.start();
    return atomixCluster;
  }
}
