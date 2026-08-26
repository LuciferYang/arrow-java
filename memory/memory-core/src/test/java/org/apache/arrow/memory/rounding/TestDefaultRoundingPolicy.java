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
package org.apache.arrow.memory.rounding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Tests that invalid {@link DefaultRoundingPolicy} system-property values fall back to defaults
 * instead of failing the static initializer.
 *
 * <p>The chunk size is computed in a static initializer, so each case runs a probe in a fresh JVM
 * to observe the initialization independently of this test JVM's properties.
 */
public class TestDefaultRoundingPolicy {

  private static final long DEFAULT_CHUNK_SIZE = 8192L << 11;

  private static final String PAGE_SIZE_PROPERTY = "org.apache.memory.allocator.pageSize";
  private static final String MAX_ORDER_PROPERTY = "org.apache.memory.allocator.maxOrder";

  /** Probe main: prints the default chunk size, initializing DefaultRoundingPolicy. */
  public static class Probe {
    public static void main(String[] args) {
      System.out.println("chunkSize=" + DefaultRoundingPolicy.DEFAULT_ROUNDING_POLICY.chunkSize);
    }
  }

  private static long runProbeChunkSize(String... jvmArgs) throws Exception {
    final String classpath = System.getProperty("java.class.path");
    final String[] command = new String[3 + jvmArgs.length + 1];
    command[0] = System.getProperty("java.home") + "/bin/java";
    command[1] = "-cp";
    command[2] = classpath;
    System.arraycopy(jvmArgs, 0, command, 3, jvmArgs.length);
    command[command.length - 1] = Probe.class.getName();

    final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    // The probe prints a single short line, so reading after a bounded wait cannot block.
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      fail("probe JVM did not finish in time");
    }
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.exitValue(), "probe JVM failed:\n" + output);

    long chunkSize = -1;
    for (String line : output.split("\\R")) {
      if (line.startsWith("chunkSize=")) {
        chunkSize = Long.parseLong(line.substring("chunkSize=".length()).trim());
      }
    }
    assertTrue(chunkSize >= 0, "no chunkSize= marker in probe output:\n" + output);
    return chunkSize;
  }

  @Test
  public void defaultsApplyWithoutSystemProperties() throws Exception {
    assertEquals(DEFAULT_CHUNK_SIZE, runProbeChunkSize());
  }

  @Test
  public void validPageSizeIsHonored() throws Exception {
    assertEquals(16384L << 11, runProbeChunkSize("-D" + PAGE_SIZE_PROPERTY + "=16384"));
  }

  @Test
  public void validMaxOrderIsHonored() throws Exception {
    assertEquals(8192L << 8, runProbeChunkSize("-D" + MAX_ORDER_PROPERTY + "=8"));
  }

  @Test
  public void maxOrderZeroUsesPageSizeAsChunkSize() throws Exception {
    assertEquals(8192L, runProbeChunkSize("-D" + MAX_ORDER_PROPERTY + "=0"));
  }

  @Test
  public void invalidPageSizeFallsBackToDefaults() throws Exception {
    assertEquals(DEFAULT_CHUNK_SIZE, runProbeChunkSize("-D" + PAGE_SIZE_PROPERTY + "=5000"));
  }

  @Test
  public void nonNumericPageSizeFallsBackToDefaults() throws Exception {
    assertEquals(DEFAULT_CHUNK_SIZE, runProbeChunkSize("-D" + PAGE_SIZE_PROPERTY + "=abc"));
  }

  @Test
  public void pageSizeTooLargeForMaxOrderFallsBackToDefaultPageSize() throws Exception {
    // 1MB << 11 exceeds the maximum chunk size; the initializer must fall back
    // rather than throw ExceptionInInitializerError (breaking every RootAllocator).
    assertEquals(DEFAULT_CHUNK_SIZE, runProbeChunkSize("-D" + PAGE_SIZE_PROPERTY + "=1048576"));
  }

  @Test
  public void negativeMaxOrderFallsBackToDefaultOrderAndKeepsPageSize() throws Exception {
    assertEquals(
        16384L << 11,
        runProbeChunkSize("-D" + PAGE_SIZE_PROPERTY + "=16384", "-D" + MAX_ORDER_PROPERTY + "=-5"));
  }

  @Test
  public void maxOrderTooLargeFallsBackToDefaultOrderAndKeepsPageSize() throws Exception {
    assertEquals(
        16384L << 11,
        runProbeChunkSize("-D" + PAGE_SIZE_PROPERTY + "=16384", "-D" + MAX_ORDER_PROPERTY + "=15"));
  }
}
