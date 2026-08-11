# Mio architecture verification

This module is intentionally test-only. Konsist reads the production source tree from the `mio.project.root` system property, while the fixture sources stay outside `src/test/kotlin` so they are parsed without becoming production or test dependencies.

When a production pattern first appears, the same task must add:

1. the production Konsist rule;
2. one passing representative fixture;
3. one failing violation fixture;
4. the new rule call in `ProductionArchitectureKonsistTest`;
5. root verify passing.

This is the mandatory rule-evolution workflow. The fixture test must pair every file with an explicit synthetic owner module; rules must not infer ownership from a fixture directory name.

| First production pattern | Owning task | Mandatory rule family |
|---|---:|---|
| Repository interfaces and initial domain models | 03 | domain ownership, immutability, repository contract boundaries |
| Repository implementations, entities, DAOs, and data sources | 04 | suffix/location, forbidden escape, one-mechanism data-source boundaries |
| Shared network/provider contracts | 05 | network-library ownership, canonical provider-model ownership, forbidden-escape rules |
| Metro graphs and contributions | 06 | graph ownership and allowed injection patterns |
| Navigation keys and entry builders | 07 | API/impl ownership, serialization, allowed key payloads |
| Shared UI and first representative Route/Screen pair | 09 | shared ownership, stateless Screen shape, same-file Route/Screen convention |
| Platform services and logging implementation | 10 | dispatcher, clock, credential, and logging boundary details |
| First ViewModel and complete destination pair | 12 | ViewModel state/action/event contract and Route/Screen/ViewModel separation |

The README is a handoff contract, not a waiver. Later tasks must update the rules when the type first exists; task 02 does not guess annotation names or APIs ahead of those tickets. The acceptance criteria in tasks 03, 04, 07, 09, and 12 already require their applicable architecture checks.

Task 04's persistence ownership, persistence escape, and one-mechanism data-source rules are implemented. Task 11 and task 20 must evolve these rules when migrations, outbox, and reconciliation types appear.

Task 05's network and canonical provider-contract ownership rules are implemented. Tasks 13 and 14 must extend the DTO/service ownership fixtures when those first concrete provider types appear.

Task 06's Metro graph ownership, constructor-injection, graph-creation, and no-static-holder rules are implemented. Task 10 must evolve the clock, dispatcher, and credential boundaries, and task 12 must evolve the ViewModel injection boundary.
