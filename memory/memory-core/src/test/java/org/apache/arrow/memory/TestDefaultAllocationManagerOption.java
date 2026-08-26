/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.arrow.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.apache.arrow.memory.DefaultAllocationManagerOption.AllocationManagerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class TestDefaultAllocationManagerOption {

  private static final String PROPERTY_NAME =
      DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_PROPERTY_NAME;

  private static final String ENV_NAME =
      DefaultAllocationManagerOption.ALLOCATION_MANAGER_TYPE_ENV_NAME;

  @AfterEach
  public void clearProperty() {
    System.clearProperty(PROPERTY_NAME);
  }

  @Test
  public void testUnspecifiedTypeIsUnknown() {
    // The environment variable cannot be cleared inside the JVM.
    Assumptions.assumeTrue(System.getenv(ENV_NAME) == null, ENV_NAME + " is set");
    System.clearProperty(PROPERTY_NAME);
    assertEquals(
        AllocationManagerType.Unknown,
        DefaultAllocationManagerOption.getDefaultAllocationManagerType());
  }

  @Test
  public void testExactCaseValueIsHonored() {
    System.setProperty(PROPERTY_NAME, "Unsafe");
    assertEquals(
        AllocationManagerType.Unsafe,
        DefaultAllocationManagerOption.getDefaultAllocationManagerType());
  }

  @Test
  public void testLowerCaseValueIsHonored() {
    System.setProperty(PROPERTY_NAME, "unsafe");
    assertEquals(
        AllocationManagerType.Unsafe,
        DefaultAllocationManagerOption.getDefaultAllocationManagerType());
  }

  @Test
  public void testValueWithSurroundingWhitespaceIsHonored() {
    System.setProperty(PROPERTY_NAME, " Netty ");
    assertEquals(
        AllocationManagerType.Netty,
        DefaultAllocationManagerOption.getDefaultAllocationManagerType());
  }

  @Test
  public void testInvalidValueIsIgnoredWithWarning() {
    // With the environment variable set, an invalid property keeps the env-derived type.
    Assumptions.assumeTrue(System.getenv(ENV_NAME) == null, ENV_NAME + " is set");

    Logger logger = (Logger) LoggerFactory.getLogger(DefaultAllocationManagerOption.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      System.setProperty(PROPERTY_NAME, "unsaef");
      assertEquals(
          AllocationManagerType.Unknown,
          DefaultAllocationManagerOption.getDefaultAllocationManagerType());

      List<ILoggingEvent> warnings = appender.list;
      assertEquals(1, warnings.size());
      assertEquals(Level.WARN, warnings.get(0).getLevel());
      assertTrue(warnings.get(0).getFormattedMessage().contains("'unsaef'"));
      assertTrue(warnings.get(0).getFormattedMessage().contains(PROPERTY_NAME));
    } finally {
      logger.detachAppender(appender);
    }
  }
}
