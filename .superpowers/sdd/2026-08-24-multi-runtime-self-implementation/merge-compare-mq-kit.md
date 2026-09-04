# Merge Compare: ddd4j-mq + ddd4j-kit (2.0.x -> 3.0.x)

**Date**: 2026-08-27
**Branch baseline**: feature/3.0.x (checked out)
**Comparison**: feature/2.0.x vs feature/3.0.x

---

## Summary

| Category | Count | Description |
|----------|-------|-------------|
| A (noise/keep 3.0.x) | 5 | License headers, import package migration (Jackson 2->3), 3.0.x-only new files |
| B (logic-port from 2.0.x) | 0 | No logic changes needed |
| C (arbitration needed) | 0 | No items |

**Conclusion**: 3.0.x is a strict superset of 2.0.x for both modules. All differences are either version migration noise (Jackson 2->3 API adaptation) or 3.0.x-only new features (MqDomainEventPublisher + DomainEventCarrier). No merge actions required.

---

## File-by-File Decision Table

### ddd4j-mq module

| # | File | Lines Changed | Classification | Rationale |
|---|------|--------------|----------------|-----------|
| 1 | `ddd4j-mq-core/.../event/MqDomainEventPublisher.java` | 136 (new) | **A** | 3.0.x-only new file. Does not exist on 2.0.x. Implements DomainEventPublisher -> MQ bridge. Keep as-is. |
| 2 | `ddd4j-mq-core/.../event/MqDomainEventPublisherTest.java` | 223 (new) | **A** | 3.0.x-only new file. 14 tests covering publish/publishAll/toCarrier + null/non-DomainEvent defense. Keep as-is. |
| 3 | `ddd4j-mq-core/.../event/DomainEventCarrier.java` | 69 (new) | **A** | 3.0.x-only new file. MQ carrier for serialized domain events. Uses correct Jackson 3 annotations (com.fasterxml.jackson.annotation.* is unchanged in Jackson 3). Keep as-is. |

### ddd4j-kit module

| # | File | Lines Changed | Classification | Rationale |
|---|------|--------------|----------------|-----------|
| 4 | `ddd4j-kit/.../lang/JsonKit.java` | ~80 lines | **A** | Pure Jackson 2->3 API migration. All differences are: import `com.fasterxml.jackson.*` -> `tools.jackson.*`, method renames (`defaultPropertyInclusion` -> `changeDefaultPropertyInclusion`, `visibility` -> `changeDefaultVisibility`), serializer class changes (`JsonSerializer` -> `ValueSerializer`, `JsonDeserializer` -> `ValueDeserializer`), `fieldNames()` -> `propertyNames().iterator()`, removal of redundant helper methods (replaced by javatime ext serializers). Zero logic change. |
| 5 | `ddd4j-kit/.../text/StrPool.java` | 2 lines | **A** | 3.0.x adds `DDD4J_PREFIX = "ddd4j."` and `MS = "ms"` constants + ddd4j license header. 2.0.x lacks these (has baomidou license). 3.0.x is superset. Keep as-is. |

---

## Noise Files (all A-class, no substantive logic diff)

The following files differ between branches but have **zero substantive logic changes** (only license header / import package / format differences):

**ddd4j-kit** (31 files): AESKit, HMACKit, SM3Kit, SM4Kit, SymmetricCryptoKit, AppKit, ArithKit, ArrayKit, BeanKit, CollKit, DataSizeKit, DateKit, FastdfsKit, FunctionKit, GraphKit, IdKit, LotteryKit, MapKit, NumKit, ObjKit, PinyinKit, RandomKit, RankKit, RankSortTypeEnum, ReflectKit, StreamKit, StrKit, ThreadKit, TimeKit, AntPathMatcher, PathMatcher, IpKit

**ddd4j-mq** (70+ files): All MQClient implementations (ActiveMQ, Kafka, MQTT, NATS, ONS, Pulsar, RabbitMQ, Redis Stream, RocketMQ, SQS, TDMQ, Disruptor), all delivery infrastructure, all Spring config, all message/acknowledgment types -- all are license/import noise only.

---

## Implementation Commits

**None required.** All 5 files with substantive diffs are A-class (keep 3.0.x as-is).

---

## Test Results

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (55.8s)
```

Modules tested:
- `ddd4j-kit` -- 0 test failures
- `ddd4j-mq-core` -- 45 tests pass (including 14 MqDomainEventPublisherTest + 7 MQOutboxDispatcherTest + others)
- Dependencies resolved via `-am`: ddd4j, ddd4j-dependencies, ddd4j-annotation, ddd4j-core

---

## C-Class Arbitration List

**Empty.** No items require arbitration.

---

## Observations

1. **MqDomainEventPublisher is 3.0.x-only**: This is a new feature (E6) that bridges core DomainEventPublisher to MQ. It does not exist on 2.0.x. No porting needed.

2. **DomainEventCarrier uses `com.fasterxml.jackson.annotation.*`**: This is correct for Jackson 3. Jackson 3 moved core/databind packages to `tools.jackson.*` but kept annotations at `com.fasterxml.jackson.annotation.*`.

3. **2.0.x has no unique MQ logic**: The 2.0.x branch has no MQ-specific business logic (message deduplication, delivery strategies, etc.) that3.0.x is missing. All delivery infrastructure (outbox, inbox) exists on both branches with only noise differences.

4. **StrPool 3.0.x additions**: `DDD4J_PREFIX` and `MS` constants are 3.0.x additions not present on 2.0.x. These are used by other 3.0.x modules and should remain.
