# ADR 0001: Define bounded contexts and context map

Date: 2025-09-18
Status: Accepted

## Context
We are building a microservices-based Travel Agent system. To avoid model corruption and clarify service responsibilities, we need explicit bounded contexts and a context map.

## Decision
- Establish the following bounded contexts: Booking, Payment, Profile, Assistant.
- Use asynchronous messaging (Kafka) between Booking (upstream) and Payment (downstream) for booking lifecycle events.
- Keep a thin shared contracts module (`common`) for integration primitives only (e.g., Event, Topics). No business entities are shared.
- Assistant interacts with other contexts primarily via synchronous HTTP.
- Document the overview in `docs/bounded-contexts.md` and create per-context ADRs (0002–0005).

## Consequences
- Clear ownership and boundaries reduce coupling and enable independent evolution.
- Event-driven integration requires schema governance and idempotency strategies.
- Shared module must remain minimal to avoid de facto shared kernel.

## References
- Context overview: ../bounded-contexts.md
