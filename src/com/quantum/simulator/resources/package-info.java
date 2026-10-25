/**
 * Resource requests, allocations, capacity tracking, and utilization statistics.
 *
 * <p>{@link com.quantum.simulator.resources.ResourceRequest}, built through
 * {@link com.quantum.simulator.resources.ResourceRequest.Builder}, describes the
 * resources a caller needs. {@link com.quantum.simulator.resources.ResourceManager}
 * checks requests against
 * {@link com.quantum.simulator.config.ResourceConstraints} and returns a
 * {@link com.quantum.simulator.resources.ResourceAllocation} or throws
 * {@link com.quantum.simulator.resources.ResourceManager.ResourceAllocationException}.
 * {@link com.quantum.simulator.resources.ResourceStatus},
 * {@link com.quantum.simulator.resources.ResourceUtilization}, and
 * {@link com.quantum.simulator.resources.ResourceStatistics} report capacity and
 * allocation counters.</p>
 */
package com.quantum.simulator.resources;
