# ADR 0003: Payment bounded context

Date: 2025-09-18
Status: Accepted

## Context
Payment is responsible for authorizing and capturing payments associated with bookings. It reacts to booking lifecycle events published by Booking.

## Decision
- Define a Payment bounded context that subscribes to booking events and executes payment flows.
- Consume events from Kafka topic `travel.bookings` (e.g., BOOKING_CREATED).
- Keep Payment business rules and models private to the context; do not rely on Booking’s internal models.
- Provide observability and idempotency for event handling (design in future ADRs).

## Scope
- Payment authorization and capture
- Idempotent processing of events
- Emitting payment outcomes (future): PAYMENT_AUTHORIZED, PAYMENT_FAILED

## Ubiquitous Language
- Payment, Authorization, Capture, Refund, PaymentId, Amount, Currency, Status

## Integration
- Input: Kafka topic travel.bookings
- Output: (future) Kafka topic(s) for payment outcomes, or callbacks
- Synchronous API: (future) Payment status query endpoint

## Consequences
- Loose coupling via asynchronous messaging with Booking
- Requires contracts and schemas for events and potential retries/backoff
