/**
 * Event scheduling and dispatch for the simulator.
 *
 * <p>An event is represented by {@link QuantumEvent}, ordered by scheduled time,
 * {@link EventPriority}, and construction sequence, and stored in an {@link EventQueue}. An
 * {@link EventDispatcher} drains the queue, executes events against a simulation context,
 * and notifies registered listeners. {@link EventType} classifies the kind of work an event
 * represents.
 */
package com.quantum.simulator.events;
