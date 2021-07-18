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

import io.github.agebhar1.CachingMultiGauge.Row;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingMultiGaugeTests {

    private final static Offset<Double> epsilon = Offset.offset(1e-10);

    @Test
    public void shouldUpdateValuesAfterTTLExpired() {

        final AtomicInteger invocationCounter = new AtomicInteger(0);
        final MockClock mockClock = new MockClock();

        final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, mockClock);

        CachingMultiGauge
                .builder("row")
                .ttl(Duration.ofSeconds(2))
                .update(cache -> {

                    double value = invocationCounter.incrementAndGet();

                    cache.update(Tags.of("column0", "a").and("column1", "a"), value);
                    cache.update(Tags.of("column0", "a").and("column1", "b"), value);
                    cache.update(Tags.of("column0", "b").and("column1", "b"), value);
                    cache.update(Tags.of("column0", "b").and("column1", "a"), value);

                })
                .tag("key", "value")
                .register(registry)
                .register(
                        Row.of(Tag.of("column0", "a"), Tag.of("column1", "a")),
                        Row.of(Tag.of("column0", "a"), Tag.of("column1", "b")),
                        Row.of(Tag.of("column0", "b"), Tag.of("column1", "b")),
                        Row.of(Tag.of("column0", "b"), Tag.of("column1", "a"))
                );

        assertThat(invocationCounter).hasValue(0);

        assertThat(registry.get("row").tag("key", "value").tag("column0", "a").tag("column1", "a").gauge().value()).isCloseTo(1, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "a").tag("column1", "b").gauge().value()).isCloseTo(1, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "b").tag("column1", "b").gauge().value()).isCloseTo(1, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "b").tag("column1", "a").gauge().value()).isCloseTo(1, epsilon);

        assertThat(invocationCounter).hasValue(1);

        mockClock.addSeconds(2);

        assertThat(registry.get("row").tag("key", "value").tag("column0", "a").tag("column1", "a").gauge().value()).isCloseTo(1, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "a").tag("column1", "b").gauge().value()).isCloseTo(1, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "b").tag("column1", "b").gauge().value()).isCloseTo(1, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "b").tag("column1", "a").gauge().value()).isCloseTo(1, epsilon);

        assertThat(invocationCounter).hasValue(1);

        mockClock.addSeconds(1);

        assertThat(registry.get("row").tag("key", "value").tag("column0", "a").tag("column1", "a").gauge().value()).isCloseTo(2, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "a").tag("column1", "b").gauge().value()).isCloseTo(2, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "b").tag("column1", "b").gauge().value()).isCloseTo(2, epsilon);
        assertThat(registry.get("row").tag("key", "value").tag("column0", "b").tag("column1", "a").gauge().value()).isCloseTo(2, epsilon);

        assertThat(invocationCounter).hasValue(2);

    }

}
