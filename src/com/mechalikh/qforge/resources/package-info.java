/**
 * Resource requests, allocations, capacity tracking, and utilization statistics.
 *
 * <p>{@link com.mechalikh.qforge.resources.ResourceRequest}, built through
 * {@link com.mechalikh.qforge.resources.ResourceRequest.Builder}, describes the
 * resources a caller needs. {@link com.mechalikh.qforge.resources.ResourceManager}
 * checks requests against
 * {@link com.mechalikh.qforge.config.ResourceConstraints} and returns a
 * {@link com.mechalikh.qforge.resources.ResourceAllocation} or throws
 * {@link com.mechalikh.qforge.resources.ResourceManager.ResourceAllocationException}.
 * {@link com.mechalikh.qforge.resources.ResourceStatus},
 * {@link com.mechalikh.qforge.resources.ResourceUtilization}, and
 * {@link com.mechalikh.qforge.resources.ResourceStatistics} report capacity and
 * allocation counters.</p>
 */
package com.mechalikh.qforge.resources;
