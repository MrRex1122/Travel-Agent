# ADR 0002: Booking bounded context

Date: 2025-09-18
Status: Accepted

## Context
Booking is responsible for creating and managing bookings. It is an upstream context for Payment via domain events.

## Decision
- Define a Booking bounded context owning booking lifecycle and validation rules.
- Publish booking lifecycle events to Kafka topic `travel.bookings`.
- Expose REST API for booking creation (currently POST /api/bookings).
- Keep integration contracts (Event, Topics) in `common` as technical primitives; domain entities remain private to Booking.

## Scope
- Create Booking
- Validate request (user exists, price rules) [future]
- Emit events: BOOKING_CREATED (and future: BOOKING_CANCELLED, BOOKING_CONFIRMED)

## Ubiquitous Language
- Booking, Trip, Itinerary, BookingId, UserId, Price, Status, BookingCreated

## APIs
- HTTP: POST /api/bookings -> 202 with publication status (current minimal implementation)
- Future: GET /api/bookings/{id}

## Events
- Topic: travel.bookings
- Event type(s): BOOKING_CREATED (payload includes userId, tripId, price)
- Contract: JSON payload; move to typed DTO when schema registry is introduced

## Dependencies
- Downstream: Payment (consumes events)
- Upstream data (read): Profile (user validation) [future]

## Consequences
- Payment decoupled from Booking through asynchronous messaging
- Requires idempotent event publication and traceability (future observability)
