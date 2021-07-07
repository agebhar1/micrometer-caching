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
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;

import java.time.Duration;
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

    private static class CacheOperationsImpl implements CacheOperations {

        private final Map<String, Double> values;

        CacheOperationsImpl(Map<String, Double> values) {
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

    private final Map<String, Double> values = new TreeMap<>();

    private final Consumer<CacheOperations> consumer;

    private final Clock clock;

    private final long ttlNanos;

    private long lastUpdateNanos;

    private CachingMultiGauge(long ttlNanos, Clock clock, Consumer<CacheOperations> consumer) {
        this.ttlNanos = ttlNanos;
        this.clock = clock;
        this.consumer = consumer;
        this.lastUpdateNanos = clock.monotonicTime() - ttlNanos - 1;
    }

    public double getValue(String key) {
        synchronized (this) {
            final long timestampNanos = clock.monotonicTime();
            if (isTTLExpired(timestampNanos, lastUpdateNanos)) {
                lastUpdateNanos = timestampNanos;
                consumer.accept(new CacheOperationsImpl(values));
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

    public Iterable<MultiGauge.Row<?>> rows(Iterable<Tags> uniqueRowTags) {
        return StreamSupport.stream(uniqueRowTags.spliterator(), false)
                .map(tags -> {

                    final String key = keyOf(tags);
                    final ToDoubleFunction<CachingMultiGauge> f = it -> it.getValue(key);

                    return MultiGauge.Row.of(tags, this, f);
                })
                .collect(toList());
    }

    public static class CachingMultiGaugeBuilder {

        private final Duration ttl;
        private final Clock clock;

        private CachingMultiGaugeBuilder(Duration ttl, Clock clock) {
            this.ttl = ttl;
            this.clock = clock;
        }

        public CachingMultiGauge update(Consumer<CacheOperations> consumer) {
            return new CachingMultiGauge(ttl.toNanos(), clock, consumer);
        }

    }

    public static CachingMultiGaugeBuilder ttl(Duration duration, Clock clock) {
        return new CachingMultiGaugeBuilder(duration, clock);
    }

}
