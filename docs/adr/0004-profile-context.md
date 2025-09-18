# ADR 0004: Profile bounded context

Date: 2025-09-18
Status: Accepted

## Context
Profile manages user data, preferences, and loyalty information. Other contexts may reference user information but must not own or mutate it.

## Decision
- Define a Profile bounded context responsible for user profile CRUD and data governance.
- Provide synchronous APIs for read/write of user profiles to other contexts (e.g., Booking for validation, Assistant for personalization).
- Keep PII handling and privacy compliance inside Profile.

## Scope
- User profiles (identity data, contacts)
- Preferences and loyalty program data
- Privacy and data retention policies

## Ubiquitous Language
- UserProfile, UserId, Contact, Preference, LoyaltyTier, GDPR/Consent

## Integration
- Synchronous HTTP APIs for reads (and writes where authorized)
- Events (future): PROFILE_UPDATED for cache invalidation in consumers

## Consequences
- Centralized user data governance
- Other contexts depend on Profile for authoritative user info
