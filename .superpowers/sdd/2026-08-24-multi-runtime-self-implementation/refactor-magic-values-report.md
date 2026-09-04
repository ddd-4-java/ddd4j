# Magic Values Refactoring Report

## Summary

Completed magic value cleanup across 6 modules on `feature/3.0.x`. All 10 modules' tests pass.

## Commits

| # | Commit | Module | Description |
|---|--------|--------|-------------|
| 1 | `42a62fdb` | kit | StrPool 新增 DDD4J_PREFIX / MS |
| 2 | `21cb5bf5` | core | 新增 EventStoreConstants / ProjectionConstants |
| 3 | `aea484e0` | data | EventStore 实现替换为 EventStoreConstants |
| 4 | `3c5f2186` | runtime | 投影位置/Micrometer/Guice 常量替换 |
| 5 | `5bcafb85` | metrics | OTel 投影指标常量替换为 ProjectionConstants / StrPool.MS |

## New Constants

### StrPool.java (ddd4j-kit)

| Constant | Value | Purpose |
|----------|-------|---------|
| `DDD4J_PREFIX` | `"ddd4j."` | Guice @Named binding key 前缀 |
| `MS` | `"ms"` | 时间单位毫秒 |

### EventStoreConstants.java (ddd4j-core, new file)

| Constant | Value | Purpose |
|----------|-------|---------|
| `TABLE_NAME` | `"DDD4J_EVENT_STORE"` | 统一事件存储表名 |
| `COLUMN_AGGREGATE_ID` | `"aggregate_id"` | 聚合根标识列名 |
| `COLUMN_VERSION` | `"version"` | 版本号列名 |
| `COLUMN_POSITION` | `"position"` | 全局位置列名 |
| `COLUMN_EVENT_TYPE` | `"event_type"` | 事件类型列名 |
| `COLUMN_EVENT_ID` | `"event_id"` | 事件 ID 列名 |
| `COLUMN_PAYLOAD` | `"payload"` | 事件载荷列名 |
| `COLUMN_TIMESTAMP` | `"timestamp"` | 时间戳列名 |
| `ESDB_SYSTEM_STREAM_PREFIX` | `"$"` | EventStoreDB 系统流前缀 |
| `ESDB_DEFAULT_READ_LIMIT` | `4096L` | ESDB 默认读取条数 |

### ProjectionConstants.java (ddd4j-core, new file)

| Constant | Value | Purpose |
|----------|-------|---------|
| `TABLE_NAME` | `"DDD4J_PROJECTION_POSITION"` | 统一投影位置表名 |
| `COLUMN_STREAM_ID` | `"stream_id"` | 流 ID 列名 |
| `COLUMN_NEXT_EVENT_NUMBER` | `"next_event_number"` | 下一事件号列名 |
| `METRIC_EVENTS_TOTAL` | `"projection.events.total"` | Micrometer 事件总数指标 |
| `METRIC_RUN_DURATION` | `"projection.run.duration"` | Micrometer 运行耗时指标 |
| `METRIC_ERRORS_TOTAL` | `"projection.errors.total"` | Micrometer 运行失败指标 |
| `TAG_STREAM` | `"stream"` | Micrometer 标签名 |
| `OTel_METRIC_RUN_COUNT` | `"ddd4j.projection.run.count"` | OTel 运行次数指标 |
| `OTel_METRIC_EVENT_COUNT` | `"ddd4j.projection.event.count"` | OTel 事件计数指标 |
| `OTel_METRIC_RUN_DURATION` | `"ddd4j.projection.run.duration"` | OTel 运行耗时指标 |
| `OTel_METRIC_RUN_ERROR` | `"ddd4j.projection.run.error"` | OTel 运行错误指标 |
| `OTel_ATTR_STREAM_ID` | `"streamId"` | OTel 属性 key |

### GuiceConstants.java (ddd4j-runtime-guice, new file)

| Constant | Value | Purpose |
|----------|-------|---------|
| `VIEW_MANAGER_THREAD_POOL_SIZE_KEY` | `"ddd4j.view-manager.thread-pool-size"` | Guice @Named binding key |
| `DEFAULT_THREAD_POOL_SIZE` | `2` | 默认线程池大小 |

## Replacement Statistics

| Module | Files Changed | Replacements |
|--------|--------------|-------------|
| ddd4j-kit | 1 | 2 (new constants) |
| ddd4j-core | 2 (new) | 2 (new constant classes) |
| ddd4j-data-event-store-jpa | 1 | 8 (table + 7 column names) |
| ddd4j-data-event-store-r2dbc | 1 | 12 (table + 6 column names in SQL + 5 in toStoredEvent) |
| ddd4j-data-event-store-esdb | 1 | 3 (DEFAULT_READ_LIMIT + SYSTEM_STREAM_PREFIX + import) |
| ddd4j-runtime-spring | 2 | 6 (table + 2 column names in entity + 3 metric names + 1 tag) |
| ddd4j-runtime-quarkus | 2 | 6 (table + 2 column names in entity + 3 metric names + 1 tag) |
| ddd4j-runtime-guice | 4 | 14 (table + column names + 3 metric names + 1 tag + 2 Guice keys) |
| ddd4j-metrics | 1 | 6 (4 OTel metric names + 1 OTel attr key + 1 MS unit) |
| **Total** | **14** | **~55** |

## Test Results

All 10 modules tested green:
- `ddd4j-kit` -- pass
- `ddd4j-core` -- pass
- `ddd4j-data-event-store-jpa` -- pass
- `ddd4j-data-event-store-r2dbc` -- pass
- `ddd4j-data-event-store-esdb` -- pass
- `ddd4j-runtime-spring` -- pass
- `ddd4j-runtime-quarkus` -- pass
- `ddd4j-runtime-guice` -- pass
- `ddd4j-metrics` -- pass
- `ddd4j-mq-core` -- pass (no changes needed; DOMAIN_EVENT_TAG already named)

## Scope Notes

- ddd4j-mq: `DOMAIN_EVENT_TAG` in `MqDomainEventPublisher` was already a named constant (package-private). No additional extraction needed.
- ddd4j-core: `EventDeserializer.java` regex pattern and `EntityIdPath.java` TYPE_SEPARATOR were already named constants. No additional extraction needed.
- EntityIdRegistry `STRING_ENTITY_TYPE = "String"` already a named constant.
- ProjectionStatus / ProjectionRunInfo are records with no magic values.
