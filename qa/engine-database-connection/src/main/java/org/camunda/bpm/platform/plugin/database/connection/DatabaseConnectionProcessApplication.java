package org.camunda.bpm.platform.plugin.database.connection;

import org.camunda.bpm.application.PostDeploy;
import org.camunda.bpm.application.PreUndeploy;
import org.camunda.bpm.application.ProcessApplication;
import org.camunda.bpm.application.impl.ServletProcessApplication;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Creates a tcp server that enables the connection to the h2 in memory database from other hosts.
 */
@ProcessApplication
public class DatabaseConnectionProcessApplication extends ServletProcessApplication {

  private static Logger logger = LoggerFactory.getLogger(DatabaseConnectionProcessApplication.class);

  @PostDeploy
  public void postDeploy() {
    shutdownExistingServer();
    try {
      Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
    } catch (SQLException e) {
      logger.error("Was not able to start tcp server!" , e);
    }

  }

  @PreUndeploy
  public void preUndeploy() {
    shutdownExistingServer();
  }

  private void shutdownExistingServer() {
    try {
      Server.shutdownTcpServer("tcp://localhost:9092", "", true, true);
    } catch (SQLException e) {
      logger.debug("There was no server to shutdown", e);
    }
  }


}
