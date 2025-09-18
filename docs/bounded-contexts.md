# Bounded Contexts (Travel Agent)

Date: 2025-09-18

This document defines the bounded contexts in the Travel Agent system and how they collaborate.

## Context Map

- Booking (BC-BOOKING)
  - Purpose: Create and manage bookings; publish booking lifecycle events.
  - Upstream for: Payment (via events)
  - Downstream of: Profile (queries for user context, future)
- Payment (BC-PAYMENT)
  - Purpose: Authorize and capture payments for bookings; react to booking events.
  - Downstream of: Booking (consumes events)
- Profile (BC-PROFILE)
  - Purpose: Manage user profiles, preferences, loyalty.
  - Upstream for: Booking/Assistant (user data)
- Assistant (BC-ASSISTANT)
  - Purpose: Conversational orchestration; invokes other contexts via APIs.
  - Downstream of: Booking/Profile (calls), can publish assistant insights (future)

Integration style:
- Messaging (Kafka): booking.events -> payment (Topic: travel.bookings)
- Synchronous HTTP: Assistant -> Booking/Profile (future evolution), internal admin endpoints.

## Shared Kernel vs Contracts

- No shared domain model; shared module `common` contains only integration contracts (Event, Topics). Treat as shared contracts, not business entities.
- Each context owns its domain model and persistence (TBD).

## Ubiquitous Language (UL) per Context

- Booking: Booking, Trip, Itinerary, BookingId, UserId, Price, BookingCreated
- Payment: Payment, Authorization, Capture, Refund, PaymentId, Status
- Profile: UserProfile, LoyaltyTier, Preference, Contact
- Assistant: Conversation, Tool, Prompt, Intent

## Ownership and Boundaries

- Booking owns: Booking creation, validation rules, publication of booking events.
- Payment owns: Payment processing, idempotency, reconciliation, payment status.
- Profile owns: User data CRUD, privacy, PII handling.
- Assistant owns: Conversational flows, tool selection, orchestration; must not embed other contexts’ business rules.

## Context Interactions

- Booking -> Payment: Asynchronous event (BOOKING_CREATED) on `travel.bookings`.
- Assistant -> Booking/Profile: HTTP calls (planned) to serve conversational actions.

## Non-Goals

- No direct DB sharing between contexts.
- No cross-context aggregates.

## Future Work

- Introduce more specific event schemas (e.g., BookingCreatedEvent DTO) and schema registry.
- Define additional topics for payment outcomes.
- Document OpenAPI for synchronous endpoints.
