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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link BaseAllocator.Reservation} failure paths. */
public class TestReservationFailure {

  private static final AllocationManager.Factory FAILING_FACTORY =
      new AllocationManager.Factory() {

        @Override
        public AllocationManager create(BufferAllocator accountingAllocator, long requestedSize) {
          throw new OutOfMemoryError("simulated allocation failure");
        }

        @Override
        public ArrowBuf empty() {
          return DefaultAllocationManagerFactory.FACTORY.empty();
        }
      };

  /** Fails only allocations of the given size; delegates everything else. */
  private static AllocationManager.Factory failForSize(long failingSize) {
    return new AllocationManager.Factory() {

      @Override
      public AllocationManager create(BufferAllocator accountingAllocator, long requestedSize) {
        if (requestedSize == failingSize) {
          throw new OutOfMemoryError("simulated allocation failure");
        }
        return DefaultAllocationManagerFactory.FACTORY.create(accountingAllocator, requestedSize);
      }

      @Override
      public ArrowBuf empty() {
        return DefaultAllocationManagerFactory.FACTORY.empty();
      }
    };
  }

  @Test
  public void testFailedAllocateBufferReleasesReservationExactlyOnce() {
    try (RootAllocator allocator =
        new RootAllocator(
            BaseAllocator.configBuilder()
                .maxAllocation(Long.MAX_VALUE)
                .allocationManagerFactory(FAILING_FACTORY)
                .build())) {

      final AllocationReservation reservation = allocator.newReservation();
      assertTrue(reservation.add(1024L));
      assertEquals(1024, allocator.getAllocatedMemory());

      assertThrows(OutOfMemoryError.class, reservation::allocateBuffer);
      assertTrue(reservation.isUsed());
      assertEquals(0, allocator.getAllocatedMemory());

      // The failed allocateBuffer() already released the reserved bytes;
      // closing the reservation must not release them a second time.
      reservation.close();
      assertEquals(0, allocator.getAllocatedMemory());
    }
  }

  @Test
  public void testFailedAllocateBufferDoesNotUnderAccountWithBackgroundAllocations() {
    try (RootAllocator allocator =
            new RootAllocator(
                BaseAllocator.configBuilder()
                    .maxAllocation(Long.MAX_VALUE)
                    .allocationManagerFactory(failForSize(1024))
                    .build());
        ArrowBuf background = allocator.buffer(2048)) {

      assertEquals(2048, allocator.getAllocatedMemory());

      final AllocationReservation reservation = allocator.newReservation();
      assertTrue(reservation.add(1024L));
      assertEquals(3072, allocator.getAllocatedMemory());

      assertThrows(OutOfMemoryError.class, reservation::allocateBuffer);
      reservation.close();

      // With a live background allocation the double release would not throw;
      // it would silently drop the accounting by the reserved amount.
      assertEquals(2048, allocator.getAllocatedMemory());
    }
  }
}
