# ADR 0005: Assistant bounded context

Date: 2025-09-18
Status: Accepted

## Context
Assistant provides a conversational interface that helps users perform tasks (e.g., create a booking, check profile info). It orchestrates actions across other bounded contexts without owning their business rules.

## Decision
- Define an Assistant bounded context focused on conversation management, intent detection, and tool execution.
- Use synchronous HTTP to call other contexts’ public APIs (e.g., Booking, Profile). Do not reach into their databases or internal models.
- Keep LLM/provider integration (e.g., Ollama/OpenAI) encapsulated within Assistant.
- Introduce a Tool abstraction to model capabilities that call external services.

## Scope
- Conversation handling and prompt construction
- Tooling to invoke external APIs (Booking/Profile, future Payment status)
- Safety/guardrails and result summarization (future)

## Ubiquitous Language
- Conversation, Turn, Intent, Tool, Action, Result, Prompt, Context

## Integration
- Outbound HTTP calls to Booking/Profile (and others as they expose APIs)
- (Future) May subscribe to select events for context enrichment, but primary pattern is request-driven

## Boundaries
- Assistant must not implement Booking or Payment business rules; it only orchestrates calls and interprets results
- Errors and retries are handled at orchestration layer; business invariants remain within the owning contexts

## Consequences
- Enables a unified user experience while preserving clear domain ownership
- Requires robust error handling, timeouts, and circuit breaking for cross-context calls
