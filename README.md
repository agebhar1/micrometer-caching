[![License Apache2](https://img.shields.io/badge/License-Apache2-blue.svg)](https://github.com/agebhar1/micrometer-caching/blob/master/LICENSE)
[![Build Status](https://github.com/agebhar1/micrometer-caching/actions/workflows/maven.yml/badge.svg)](https://github.com/agebhar1/micrometer-caching/actions?query=branch%3Amain)

# Purpose

Provides cacheable [Micrometer](https://github.com/micrometer-metrics/micrometer) gauges for fine-grained gauges (group) with expensive calculations/recall, e.g., database query w/
grouping. Data should be only reevaluated after cache expires and for whole group.   

See [CachingMultiGaugeTests](/src/test/java/io/github/agebhar1/CachingMultiGaugeTests.java).