/*
 * Copyright © 2021 Andreas Gebhardt (agebhar1@googlemail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.agebhar1;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class CachingMultiGauge {

    public interface CacheOperations {

        void clear();

        void update(Tags tags, double value);

    }

    private static class Cache implements CacheOperations {

        private final Map<String, Double> values;

        Cache(Map<String, Double> values) {
            this.values = values;
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public void update(Tags tags, double value) {
            values.put(tags.toString(), value);
        }
    }

    private final MultiGauge gauge;

    private final Map<String, Double> values = new TreeMap<>();

    private final Consumer<CacheOperations> consumer;

    private final long ttlNanos;

    private final Clock clock;

    private long lastUpdateNanos;

    private CachingMultiGauge(MeterRegistry registry, String name, Tags tags, String baseUnit, String description, Duration ttl, Consumer<CacheOperations> consumer) {

        this.gauge = MultiGauge
                .builder(name)
                .tags(tags)
                .baseUnit(baseUnit)
                .description(description)
                .register(registry);

        this.clock = registry.config().clock();
        this.ttlNanos = ttl.toNanos();
        this.consumer = consumer;
        this.lastUpdateNanos = clock.monotonicTime() - ttlNanos - 1;
    }

    public double getValue(String key) {
        synchronized (this) {
            final long timestampNanos = clock.monotonicTime();
            if (isTTLExpired(timestampNanos, lastUpdateNanos)) {
                lastUpdateNanos = timestampNanos;
                consumer.accept(new Cache(values));
            }
            return values.getOrDefault(key, 0.0);
        }
    }

    private boolean isTTLExpired(long timestampNanos, long lastUpdateNanos) {
        return timestampNanos - lastUpdateNanos > ttlNanos;
    }

    protected String keyOf(Tags tags) {
        return tags.toString();
    }

    public void register(Row... rows) {
        register(Arrays.asList(rows));
    }

    public void register(Iterable<Row> rows) {
        gauge.register(StreamSupport.stream(rows.spliterator(), false)
                .map(row -> {

                    final String key = keyOf(row.uniqueTags);
                    final ToDoubleFunction<CachingMultiGauge> fn = it -> it.getValue(key);

                    return MultiGauge.Row.of(row.uniqueTags, this, fn);
                })
                .collect(toList()));
    }

    public static Builders.CacheTTL builder(String name) {
        return new Builder(name);
    }

    public static class Builder implements Builders, Builders.CacheTTL, Builders.CacheUpdate {

        private final String name;
        private Tags tags = Tags.empty();

        private String description;
        private String baseUnit;

        private Duration ttl;
        private Consumer<CacheOperations> update;

        public Builder(String name) {
            this.name = name;
        }

        @Override
        public CacheUpdate ttl(Duration duration) {
            this.ttl = duration;
            return this;
        }

        @Override
        public Builders update(Consumer<CacheOperations> consumer) {
            this.update = consumer;
            return this;
        }

        @Override
        public Builders tags(String... tags) {
            return tags(Tags.of(tags));
        }

        @Override
        public Builders tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        @Override
        public Builders tag(String key, String value) {
            this.tags = this.tags.and(key, value);
            return this;
        }

        @Override
        public Builders description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public Builders baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        @Override
        public CachingMultiGauge register(MeterRegistry registry) {
            return new CachingMultiGauge(registry, name, tags, baseUnit, description, ttl, update);
        }
    }

    public interface Builders {

        interface CacheTTL {
            CacheUpdate ttl(Duration duration);
        }

        interface CacheUpdate {
            Builders update(Consumer<CacheOperations> consumer);
        }

        Builders tags(String... tags);

        Builders tags(Iterable<Tag> tags);

        Builders tag(String key, String value);

        Builders description(@Nullable String description);

        Builders baseUnit(@Nullable String unit);

        CachingMultiGauge register(MeterRegistry registry);

    }

    public static class Row {

        private final Tags uniqueTags;

        private Row(Tags uniqueTags) {
            this.uniqueTags = uniqueTags;
        }

        public static Row of(Tags uniqueTags) {
            return new Row(uniqueTags);
        }

        public static Row of(Tag... uniqueTags) {
            return new Row(Tags.of(uniqueTags));
        }

    }

}
