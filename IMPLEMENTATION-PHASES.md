# Implementation Phases (analytics-kotlin)

The implementation follows a phased approach to ensure each component is thoroughly tested before integration:

## Phase 1: State Machine Core ✅ MERGED
**Branch**: `feat/tapi-retry-phase1-state-machine`  
**PR**: #294  
**Status**: Merged to main

Created the foundational retry state machine with comprehensive test coverage:
- `RetryState` data structures with @Serializable support
- `RetryConfig` and `HttpConfig` configuration classes with validation
- `RetryStateMachine` implementing state transitions, backoff calculations, and upload gate logic
- `TimeProvider` abstraction for testable time-based logic
- 24 unit tests covering all state transitions, edge cases, and legacy mode

## Phase 2: Configuration Parsing ✅ MERGED
**Branch**: `feat/tapi-retry-phase2-config-parsing`  
**PR**: #295  
**Status**: Merged to main

Replaced manual JSON parser with typed `HttpConfig` data class using custom serializer:
- Added `HttpConfig` data class with `@Serializable` annotation
- Created `HttpConfigSerializer` for defensive parsing with graceful error handling
- Added `.validated()` methods to clamp configuration values to safe ranges
- Updated `SegmentSettings` to use `HttpConfig` instead of `JsonObject`
- 16 unit tests for config deserialization, validation, and error handling

## Phase 3: Storage Extensions ✅ PR OPEN
**Branch**: `feat/tapi-retry-phase3-storage`  
**PR**: #296  
**Status**: PR open, awaiting CI pass

Added persistence layer for RetryState using Storage extension functions:
- `Storage.Constants.RetryState` enum value for storage key
- `saveRetryState()` extension to serialize and persist state
- `loadRetryState()` extension to deserialize with default fallback
- `clearRetryState()` extension to remove persisted state
- 10 unit tests covering save/load roundtrips, corrupt data handling, null fields

## Phase 3.5: Baseline Integration Tests 📋 PLANNED
**Branch**: `feat/tapi-retry-phase3.5-baseline-tests`  
**Status**: Not started

Establish baseline integration tests using MockWebServer before Phase 4 integration:
- Add `MockWebServer` test dependency
- Create `RetryIntegrationTest.kt` to document current EventPipeline behavior
- Test current handling of 429, 5xx, 4xx responses
- Test file cleanup and successful upload flow
- Capture baseline metrics (retry behavior, file handling)

**Purpose**:
- Document current SDK behavior before Phase 4 changes
- Provide safety net during EventPipeline integration
- Enable before/after comparison
- Catch regressions in existing functionality

**Deliverables**:
- Updated `core/build.gradle` with MockWebServer dependency
- `core/src/test/kotlin/com/segment/analytics/kotlin/core/platform/RetryIntegrationTest.kt`
- Baseline test suite (4-5 tests) covering current behavior

## Phase 4: EventPipeline Integration 📋 PLANNED
**Branch**: `feat/tapi-retry-phase4-pipeline-integration`  
**Status**: Not started

Wire together state machine, config parsing, and storage into EventPipeline:
- Initialize `RetryStateMachine` in EventPipeline with config from Settings
- Add upload gate checks before batch processing
- Integrate `handleResponse()` for status code handling
- Add X-Retry-Count header using `getRetryCount()`
- Implement batch metadata file management (.meta files)
- Persist global state after state transitions
- Add integration tests using MockWebServer for new retry behavior

## Phase 5: Cleanup and Final Testing 📋 PLANNED
**Branch**: `feat/tapi-retry-phase5-cleanup`  
**Status**: Not started

Address deferred naming/configuration changes and comprehensive testing:
- Remove `maxRetryInterval` field and clamping logic (not needed)
- Rename `maxRateLimitDuration` → `maxTotalRateLimitDuration` for clarity
- Update all tests for naming changes
- Update SDD documentation
- Run full test suite across all phases
- Performance testing and optimization
- Documentation review
