package org.camunda.optimize.qa.performance.util;

import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {

  private static AtomicLong idCounter = new AtomicLong();

  public static String getNextId() {
    return String.valueOf(idCounter.getAndIncrement());
  }
}
