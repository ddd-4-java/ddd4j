/**
 * CQRS core abstractions.
 *
 * <p>CQRS is expressed by package responsibility, not by four mechanical folders:
 * command is the write side, query is the read request side, readmodel contains
 * read-side projections, positions, views and dispatchers, and segregation is
 * enforced by keeping these contracts independent from persistence and runtime
 * frameworks.</p>
 */
package io.ddd4j.core.cqrs;
