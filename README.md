<div align="center">

# ⏱️ timing-utils - A Java Timing Utility Library

**A focused, lightweight, minimal-external-dependency Java library for measuring method execution time for deliberate
performance investigations *and* permanent production logging.**

![Java](https://img.shields.io/badge/Java-21%2B-lightgrey?style=social)
![Dependencies](https://img.shields.io/badge/dependencies-SLF4J_only-lightgrey?style=social)
![Status](https://img.shields.io/badge/status-Active_development-lightgrey?style=social)
[![GitHub License](https://img.shields.io/github/license/dev-badprogrammer/timing-utils?style=social)](https://github.com/dev-badprogrammer/timing-utils/blob/main/LICENSE)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/dev-badprogrammer/timing-utils?style=social)
](https://github.com/dev-badprogrammer/timing-utils/commits/main)
[![GitHub last commit](https://img.shields.io/github/last-commit/dev-badprogrammer/timing-utils?style=social)](https://github.com/dev-badprogrammer/timing-utils/commits/main)
[![Sonar Quality Gate](https://img.shields.io/sonar/quality_gate/dev-badprogrammer_timing-utils?server=https%3A%2F%2Fsonarcloud.io&style=social)](https://sonarcloud.io/summary/overall?id=dev-badprogrammer_timing-utils)
[![Build Status](https://img.shields.io/github/actions/workflow/status/dev-badprogrammer/timing-utils/ci-build-and-test.yml?style=social)](https://github.com/dev-badprogrammer/timing-utils/actions/workflows/ci-build-and-test.yml)
[![GitHub Release Date](https://img.shields.io/github/release-date/dev-badprogrammer/timing-utils?style=social)](https://github.com/dev-badprogrammer/timing-utils/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.badprogrammer/timing-utils?style=social)](https://central.sonatype.com/artifact/dev.badprogrammer/timing-utils)

Package: `dev.badprogrammer.timing`

</div>

<br>

## Table of Contents

- [Overview](#overview)
- [Why This Library Exists](#why-this-library-exists)
- [Design Philosophy](#design-philosophy)
- [Tech Stack & Prerequisites](#tech-stack--prerequisites)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Package Structure](#package-structure)
- [Features](#features)
    - [Single Measurement — `measure` / `measureChecked`](#single-measurement--measure--measurechecked)
    - [Repeated Measurement — `measureRepeatedly` /
      `measureRepeatedlyChecked`](#repeated-measurement--measurerepeatedly--measurerepeatedlychecked)
    - [Ambient Production Timing — `TimingLogger`](#ambient-production-timing--timinglogger)
    - [Supporting Types — `TimedResult` / `TimingStatistics`](#supporting-types)
    - [Supporting Functional Interfaces — `CheckedRunnable` / `CheckedSupplier`](#supporting-functional-interfaces)
- [Design Decisions & Their Reasoning](#design-decisions--their-reasoning)
- [Best Practices](#-best-practices)
- [Testing Philosophy](#testing-philosophy)
- [Implementation Status & Roadmap](#implementation-status--roadmap)

---

## Overview

This library is simple and answers **one** question, asked in two very different contexts:

> ### "How long did that take?"

Sometimes you ask this **once, deliberately** — while investigating a performance issue, writing a benchmark, or
comparing two implementations during development. Sometimes it is **not just once**, but you want the answer **always,
silently** — a permanent fixture in production code that logs elapsed time without anyone having to remember it's even
there.

`timing-utils` solves both problems by providing one class for each context:

<div align="center">

|               | `StopWatch`                                                                     | `TimingLogger`                                              |
|---------------|---------------------------------------------------------------------------------|-------------------------------------------------------------|
| **Use case**  | Situational, deliberate measurement — benchmarking, investigations, comparisons | Permanent, ambient measurement embedded in production code  |
| **Lifecycle** | Stateless static utility                                                        | Per-invocation `AutoCloseable` resource                     |
| **Lives**     | In a test, a debugger session, a benchmark harness                              | In the production code, inside any method body, **forever** |

</div>

> [!IMPORTANT]
> Both `StopWatch` and `TimingLogger` are built around the same core idea — **measure in nanoseconds, report in
> milliseconds** — but deliberately take different shapes to solve different problems.

---

## Why This Library Exists

Most Java codebases accumulate timing code in one of two unsatisfying forms:

- Scattered `System.nanoTime()` pairs with manual subtraction, duplicated across the codebase, straightforward to
  implement, also easy to get subtly wrong — forgetting unit conversions, measuring the wrong scope, or leaking timing
  code into business logic.

- Ad-hoc `try { ... } finally { logger.debug("took {}ms", ...) }` blocks that vary in format from method to method,
  making logs hard to search or aggregate.

`timing-utils` exists to make both of these **boring** — a single, **non-invasive**, well-tested, consistent way to
answer *"How long did that take?"*, whether the answer is needed once during an investigation or every time, forever, in
production.

---

## Design Philosophy

These principles were established early in the design phase and applied across every class in the library:

- **No side effects.** Neither `StopWatch` nor `TimingLogger` retries, caches, modifies, or intercepts the measured
  method in any way. Any side effect you observe is the method's own.

- **Measure in nanos, report in millis.** `System.nanoTime()` is used internally for maximum precision. Millisecond
  conversions happen only at the point of retrieval — via `TimeUnit.NANOSECONDS.toMillis()` — so no rounding error
  accumulates during aggregation.

- **Always return, never throw (for repeated measurement).** `measureRepeatedly()` and `measureRepeatedlyChecked()`
  always return a result, even if any or every iteration fails. Failure information is **captured** and **surfaced** for
  inspection — **never rethrown**.

- **Statistics cover successful iterations only.** A failed iteration's elapsed time is meaningless on its own. Failed
  iteration timings are discarded entirely — there's no meaningful way to compare or average them against each other; an
  instant validation failure and a 30-second timeout failure are both **failures**, but their durations mean completely
  different things. Failures are represented by a count and the last exception instead — which already conveys more than
  any timing data could.

- **The right tool has the right shape.** `StopWatch` is a stateless static utility — every method receives everything
  it needs as parameters and returns everything it produces as a value, so no instance is needed. `TimingLogger` is an
  `AutoCloseable` instance because it must capture `startNanos` at `start()` and use it in `close()`, potentially much
  later — that single `long` is the state that must survive across the `try` block's lifetime. Neither is forced into
  the other's shape for the sake of consistency.

---

## Tech Stack & Prerequisites

| Requirement           | Details                                                                                                                                                      |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Java**              | 21+ — uses `var`, `String.formatted()`, `java.util.Optional`, `java.util.LongSummaryStatistics` etc.                                                         |
| **SLF4J API**         | Required at compile time for `TimingLogger` only. Works with whatever SLF4J binding you already use (Logback, Log4j2, etc.) — no changes needed on your end. |
| **Test dependencies** | JUnit 5+, Mockito — test scope only, not required by consumers.                                                                                              |

> [!NOTE]
> `StopWatch` and its supporting types (`CheckedRunnable`, `CheckedSupplier`, `TimedResult`, `TimingStatistics`) are
> **pure Java with zero dependencies**.

---

## Getting Started

### Adding `timing-utils` to Your Project

**Option 1 — Maven Central**

Add the below maven co-ordinates to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.badprogrammer</groupId>
  <artifactId>timing-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

**Option 2 — Build from source**

Clone the repository and install it to your local Maven repository:

```bash
git clone https://github.com/dev-badprogrammer/timing-utils.git
cd timing-utils
mvn clean install
```

**A minimal taste of both classes.**

Measure a method once and return the result:

```java
// One-off measurement during an investigation
final TimedResult<User> timedResult = StopWatch.measure(() -> userService.getUserById(101));
System.out.println("TimedResult: " + timedResult);
```

Terminal output:

```terminaloutput
TimedResult: TimedResult[ElapsedMillis = 24ms, ElapsedNanos = 24568257ns]
```

Measure a method repeatedly and return the aggregated statistics across all iterations:

```java
// Repeated measurement during a serious performance investigation
final TimingStatistics stats = StopWatch.measureRepeatedly(() -> userService.getUserById(101), 20, 3);
System.out.println("Stats: " + stats);
```

Terminal output:

```terminaloutput
Stats: TimingStatistics[Total iterations = 20, Successful iterations = 20, Failed iterations = 0,
Total elapsed time = 142ms, Average elapsed time = 7.120ms, Minimum elapsed time = 2ms,
Maximum elapsed time = 13ms]
```

Measure a method permanently in production and log the result automatically:

```java
// Permanent production timing
public Connection getConnection() throws SQLException {
    try (TimingLogger ignored = TimingLogger.start("getConnection", logger)) {
        return dbUtils.getConnection();
    }
}
```

Terminal output:

```terminaloutput
00:00:00.000 [main] DEBUG dev.badprogrammer.timing.util.examples.TimingLoggerDemo -- TIMED |
getConnection | Elapsed = 38ms (38459480ns)
```

### Building and Running Tests

Standard Maven build:

```bash
cd timing-utils

mvn clean install   # builds the jar and runs the full test suite
mvn test            # runs tests only
```

---

## Development Setup

This repo uses a shared git hook to enforce a 70-character limit on commit message subject lines and validates the
Conventional Commits format, preventing GitHub from truncating long titles and spilling overflow text into the PR
description box. The hook lives in a trackable `.githooks/` directory (not the untracked `.git/hooks/`), committed to
the repo and visible to anyone who clones it.
Enable it once after cloning the repository by running the below command from the root of your project.

```bash
cd timing-utils

chmod +x .githooks/commit-msg
git config core.hooksPath .githooks
```

---

## Package Structure

```
dev.badprogrammer.timing
├── function
│   ├── CheckedRunnable       — Runnable that may throw a checked exception
│   └── CheckedSupplier<T>    — Supplier<T> that may throw a checked exception
│
├── type
│   ├── TimedResult<T>        — result of a single measured invocation
│   └── TimingStatistics      — statistics from repeated invocations
│
└── util
    ├── StopWatch             — stateless utility: measure, measureRepeatedly
    └── TimingLogger          — AutoCloseable: ambient production timing
```

---

## Features

### Single Measurement — `measure` / `measureChecked`

Measures a **single** method invocation, returning its result and elapsed time wrapped in a `TimedResult<T>`.

A method that returns **`void` without declaring** any checked exceptions:

```java
// Returns void, no checked exception
final TimedResult<Void> timedResult = StopWatch.measure(() -> eventPublisher.publishEvent());
final Void result                   = timedResult.getResult();
final long elapsedMillis            = timedResult.getElapsedMillis();

System.out.println("TimedResult: " + timedResult);
System.out.println("Result: " + result);
System.out.printf("ElapsedMillis: %dms", elapsedMillis);
```

Terminal output:

```terminaloutput
TimedResult[ElapsedMillis = 3ms, ElapsedNanos = 3033608ns]
Result: null
ElapsedMillis: 3ms
```

A method that returns a **value without declaring** any checked exceptions:

```java
// Returns a value, no checked exception
final TimedResult<User> timedResult = StopWatch.measure(() -> userService.getUserById(101));
final User result                   = timedResult.getResult();
final long elapsedMillis            = timedResult.getElapsedMillis();

System.out.println("TimedResult: " + timedResult);
System.out.println("Result: " + result);
System.out.printf("ElapsedMillis: %dms", elapsedMillis);
```

Terminal output:

```terminaloutput
TimedResult: TimedResult[ElapsedMillis = 78ms, ElapsedNanos = 78284284ns]
Result: User[id=101, name=John Doe]
ElapsedMillis: 78ms
```

A method that returns **`void` and declares** a checked exception:

```java
// Return void, declares a checked exception
final TimedResult<Void> timedResult = StopWatch.measureChecked(() -> dbUtils.closeConnection());
final Void result                   = timedResult.getResult();
final long elapsedMillis            = timedResult.getElapsedMillis();

System.out.println("TimedResult: " + timedResult);
System.out.println("Result: " + result);
System.out.printf("ElapsedMillis: %dms", elapsedMillis);
```

Terminal output:

```terminaloutput
TimedResult: TimedResult[ElapsedMillis = 6ms, ElapsedNanos = 6338845ns]
Result: null
ElapsedMillis: 6ms
```

A method that returns a **value and declares** a checked exception:

```java
// Returns a value, declares a checked exception
final TimedResult<Connection> timedResult = StopWatch.measureChecked(() -> dbUtils.getConnection());
final Connection result                   = timedResult.getResult();
final long elapsedMillis                  = timedResult.getElapsedMillis();

System.out.println("TimedResult: " + timedResult);
System.out.println("Result: " + result);
System.out.printf("ElapsedMillis: %dms", elapsedMillis);
```

Terminal output:

```terminaloutput
TimedResult: TimedResult[ElapsedMillis = 11ms, ElapsedNanos = 11797399ns]
Result: org.postgresql.jdbc.PgConnection@5e9f23b4
ElapsedMillis: 11ms
```

#### `TimedResult<T>` accessors

| Method               | Returns                                             |
|----------------------|-----------------------------------------------------|
| `getResult()`        | The method's return value (`null` for void methods) |
| `getElapsedNanos()`  | Elapsed time, full precision                        |
| `getElapsedMillis()` | Elapsed time, converted from nanos to millis        |

#### Method naming convention

Every measurement method in this library comes in two variants — `measure` and `measureChecked`. The name itself tells
you which one to reach for:

| If your method                                               | Use                                  |
|--------------------------------------------------------------|--------------------------------------|
| returns **`void` without declaring** any checked exceptions  | `measure(Runnable)`                  |
| returns a **value without declaring** any checked exceptions | `measure(Supplier<T>)`               |
| returns **`void` and declares** checked exceptions           | `measureChecked(CheckedRunnable)`    |
| returns a **value and declares** checked exceptions          | `measureChecked(CheckedSupplier<T>)` |

This naming convention is used consistently for `measureRepeatedly` / `measureRepeatedlyChecked` as well, and will be
extended to the upcoming features like `compare`, `compareChecked` etc.

---

### Repeated Measurement — `measureRepeatedly` / `measureRepeatedlyChecked`

Measures a method invocation **repeatedly**, returning statistics across all **successful** invocations plus failure
tracking, wrapped in a `TimingStatistics`.

A method that returns **`void` without declaring** any checked exceptions:

```java
// Returns void, no checked exception
final TimingStatistics stats = StopWatch.measureRepeatedly(() -> eventPublisher.publishEvent(), 1_000, 5);
System.out.println("Stats: " + stats);
// System.out.println("Result: " + stats.getResult()); // does not compile — no getResult() on TimingStatistics
```

Terminal output:

```terminaloutput
Stats: TimingStatistics[Total iterations = 1000, Successful iterations = 1000, Failed iterations = 0,
Total elapsed time = 2406ms, Average elapsed time = 2.406ms, Minimum elapsed time = 2ms,
Maximum elapsed time = 12ms]
```

A method that returns a **value without declaring** any checked exceptions:

```java
// Returns a value, no checked exception
final TimingStatistics stats = StopWatch.measureRepeatedly(() -> userService.getUserById(101), 1_000, 5);
System.out.println("Stats: " + stats);
// System.out.println("Result: " + stats.getResult()); // does not compile — no getResult() on TimingStatistics
```

Terminal output:

```terminaloutput
Stats: TimingStatistics[Total iterations = 1000, Successful iterations = 1000, Failed iterations = 0,
Total elapsed time = 3671ms, Average elapsed time = 3.671ms, Minimum elapsed time = 3ms,
Maximum elapsed time = 17ms]
```

A method that returns **`void` and declares** a checked exception:

```java
// Return void, declares a checked exception
final TimingStatistics stats = StopWatch.measureRepeatedlyChecked(() -> dbUtils.closeConnection(), 1_000, 5);
System.out.println("Stats: " + stats);
// System.out.println("Result: " + stats.getResult()); // does not compile — no getResult() on TimingStatistics
```

Terminal output:

```terminaloutput
Stats: TimingStatistics[Total iterations = 1000, Successful iterations = 1000, Failed iterations = 0,
Total elapsed time = 4632ms, Average elapsed time = 4.632ms, Minimum elapsed time = 4ms,
Maximum elapsed time = 12ms]
```

A method that returns a **value and declares** a checked exception:

```java
// Returns a value, declares a checked exception
final TimingStatistics stats = StopWatch.measureRepeatedlyChecked(() -> dbUtils.getConnection(), 1_000, 5);
System.out.println("Stats: " + stats);
// System.out.println("Result: " + stats.getResult()); // does not compile — no getResult() on TimingStatistics
```

Terminal output:

```terminaloutput
Stats: TimingStatistics[Total iterations = 1000, Successful iterations = 1000, Failed iterations = 0,
Total elapsed time = 5790ms, Average elapsed time = 5.790ms, Minimum elapsed time = 5ms,
Maximum elapsed time = 14ms]
```

Failed iterations details are captured and surfaced via `hasFailures()` and `getLastException()`:

```java
// Returns a value, declares a checked exception
final TimingStatistics stats = StopWatch.measureRepeatedlyChecked(() -> dbUtils.getConnection(), 1_000, 5);
System.out.println("Stats: " + stats);
// System.out.println("Result: " + stats.getResult()); // does not compile — no getResult() on TimingStatistics

if (stats.hasFailures()) {
    stats.getLastException()
         .ifPresent(e -> System.out.printf("%d of %d iterations failed. Last exception: %s",
                                           stats.getFailedIterations(), stats.getTotalIterations(), e));
}
```

Terminal output:

```terminaloutput
Stats: TimingStatistics[Total iterations = 1000, Successful iterations = 957, Failed iterations = 43,
Total elapsed time = 5322ms, Average elapsed time = 5.561ms, Minimum elapsed time = 5ms,
Maximum elapsed time = 6ms, Last exception = org.postgresql.util.PSQLException: The connection attempt failed]

43 of 1000 iterations failed. Last exception: org.postgresql.util.PSQLException: The connection attempt failed
```

> [!WARNING]
> ~~`stats.getResult();`~~ — doesn't exist. There is no `getResult()` on `TimingStatistics`.

> [!IMPORTANT]
> Unlike `measure`/`measureChecked`, the return value of each invocation is discarded — the method passed in may or may
> not return something, but `measureRepeatedly`/`measureRepeatedlyChecked` never retain or expose it.
> Only **how long** each call took is recorded, not **what** it returned. This is why `TimingStatistics` has no
> `getResult()` — unlike `TimedResult<T>`, there is no single result to return once you've measured hundreds or
> thousands of invocations. What `TimingStatistics` reports is purely the aggregate timing behavior across all of them.

#### Warmup iterations

The first `warmupIterations` invocations run normally — their side effects happen — but their timings are **excluded**
from statistics. This prevents JVM class-loading and JIT compilation overhead from skewing the numbers, giving a true
picture of the method's steady-state performance.

#### Failure handling

If an invocation throws:

- the failure count is incremented,

- the exception is retained as the **last exception**,

- execution **continues** with the next iteration,

- and the failed iteration's timing is **excluded** from statistics.

> [!IMPORTANT]
> `measureRepeatedly` and `measureRepeatedlyChecked` **always return** a `TimingStatistics` — even if *every single
> invocation* fails. Exceptions are captured and surfaced via `hasFailures()` and `getLastException()` but never
> rethrown.

```java
// Even if all 1000 invocations fail — still returns a valid result, never throws
TimingStatistics stats = StopWatch.measureRepeatedly(() -> unavailableService(), 1_000, 0);
System.out.println("Stats: " + stats);
System.out.println("Has failures: " + stats.hasFailures());
System.out.println("Successful iterations: " + stats.getSuccessfulIterations());
System.out.println("Failed iterations: " + stats.getFailedIterations());
System.out.println("Last exception: " + stats.getLastException());
```

Terminal output:

```terminaloutput
Stats: TimingStatistics[Total iterations = 1000, Successful iterations = 0, Failed iterations = 1000,
Total elapsed time = 0ms, Average elapsed time = 0.000ms, Minimum elapsed time = 0ms,
Maximum elapsed time = 0ms, Last exception = java.lang.RuntimeException: Something went wrong]

Has failures: true
Successful iterations: 0
Failed iterations: 1000
Last exception: Optional[java.lang.RuntimeException: Something went wrong]
```

#### `TimingStatistics` accessors

| Method                                    | Returns                                                   |
|-------------------------------------------|-----------------------------------------------------------|
| `getSuccessfulIterations()`               | Count of iterations that did not throw                    |
| `getFailedIterations()`                   | Count of iterations that threw                            |
| `getTotalIterations()`                    | `getSuccessfulIterations() + getFailedIterations()`       |
| `hasFailures()`                           | `true` if any iteration threw                             |
| `getLastException()`                      | `Optional<Exception>` — the last exception thrown, if any |
| `getMinNanos()`, `getMinMillis()`         | Fastest successful iteration                              |
| `getMaxNanos()`, `getMaxMillis()`         | Slowest successful iteration                              |
| `getTotalNanos()`, `getTotalMillis()`     | Sum of elapsed time across successful iterations          |
| `getAverageNanos()`, `getAverageMillis()` | Mean elapsed time per successful iteration                |

---

### Ambient Production Timing — `TimingLogger`

A **non-invasive** `AutoCloseable` that measures a method by wrapping the method body in a try-with-resources block and
logs the elapsed time automatically when the try block exits — whether normally or with an exception — without requiring
any code restructuring.

Normal code:

```java
public Connection getConnection() throws SQLException {
    return dbUtils.getConnection();
}
```

Timed code:

```java
public Connection getConnection() throws SQLException {
    try (TimingLogger ignored = TimingLogger.start("getConnection", logger)) {
        return dbUtils.getConnection();
    }
}
```

Terminal output:

```terminaloutput
00:00:00.000 [main] DEBUG dev.badprogrammer.timing.util.examples.TimingLoggerDemo -- TIMED | getConnection |
Elapsed = 12ms (12004311ns)
```

Timed code with slow call detection:

Pass a threshold in milliseconds. If elapsed time exceeds it, the log line escalates from `DEBUG` to `WARN` —
automatically, with **no code change**:

```java
public Connection getConnection() throws SQLException {
    try (TimingLogger ignored = TimingLogger.start("getConnection", logger, 1_000)) {
        return dbUtils.getConnection();
    }
}
```

Terminal output:

```terminaloutput
// Normal call
00:00:00.000 [main] DEBUG dev.badprogrammer.timing.util.examples.TimingLoggerDemo -- TIMED | getConnection |
Elapsed = 12ms (12004311ns)

// Slow call
00:00:00.000 [main] WARN dev.badprogrammer.timing.util.examples.TimingLoggerDemo -- TIMED | getConnection |
Elapsed = 1340ms (1340291884ns) | SLOW
```

> [!TIP]
> Passing `0` (or omitting the parameter entirely) disables threshold checking — every invocation logs at `DEBUG`. `0`
> is a deliberate, valid sentinel — only **negative** values are rejected.

#### Why the caller's logger is mandatory

> [!NOTE]
> `TimingLogger.start()` requires **your** logger, not a shared internal one. This ensures timing lines appear under
> your class's name in the logs — filterable by package, routable by your existing logging configuration, and sitting
> alongside your class's other log statements. A shared logger would scatter timing lines under an unrelated logger
> name, losing all context.

#### Log levels at a glance

| Condition                          | Level   | Example |
|------------------------------------|---------|---------|
| No threshold configured            | `DEBUG` | `TIMED  | getConnection | Elapsed = 12ms (12004311ns)`            |
| Threshold configured, not exceeded | `DEBUG` | `TIMED  | getConnection | Elapsed = 12ms (12004311ns)`            |
| Threshold configured and exceeded  | `WARN`  | `TIMED  | getConnection | Elapsed = 1340ms (1340291884ns) | SLOW` |

`DEBUG` keeps timing lines invisible at typical production log levels (`INFO`, `WARN`) until you need them, while
automatic escalation to `WARN` on slow invocations surfaces problems without you having to go looking for them.

#### `close()` never throws

> [!WARNING]
> `close()` is the one method in this library with an absolute guarantee — it **never throws**, under any circumstance.
> A `close()` that throws inside try-with-resources can suppress the **original exception** from the try block, silently
> swallowing real errors. Any unexpected failure inside `close()` itself is caught and logged at `ERROR` as a last
> resort.

---

### Supporting Types

#### `TimedResult<T>` / `TimingStatistics`

`TimedResult<T>` is an immutable holder for the outcome of a method measured once — its return value and the elapsed
time.

| Method               | Returns                                             |
|----------------------|-----------------------------------------------------|
| `getResult()`        | The method's return value (`null` for void methods) |
| `getElapsedNanos()`  | Elapsed time, full precision                        |
| `getElapsedMillis()` | Elapsed time, converted from nanos to millis        |

`TimingStatistics` is an immutable holder for the aggregated statistics of a method measured repeatedly — timing
distribution, success/failure counts, and elapsed time across all iterations.

| Method                                    | Returns                                                   |
|-------------------------------------------|-----------------------------------------------------------|
| `getSuccessfulIterations()`               | Count of iterations that did not throw                    |
| `getFailedIterations()`                   | Count of iterations that threw                            |
| `getTotalIterations()`                    | `getSuccessfulIterations() + getFailedIterations()`       |
| `hasFailures()`                           | `true` if any iteration threw                             |
| `getLastException()`                      | `Optional<Exception>` — the last exception thrown, if any |
| `getMinNanos()`, `getMinMillis()`         | Fastest successful iteration                              |
| `getMaxNanos()`, `getMaxMillis()`         | Slowest successful iteration                              |
| `getTotalNanos()`, `getTotalMillis()`     | Sum of elapsed time across successful iterations          |
| `getAverageNanos()`, `getAverageMillis()` | Mean elapsed time per successful iteration                |

---

### Supporting Functional Interfaces

#### `CheckedRunnable` / `CheckedSupplier<T>`

Functional interfaces equivalent to `Runnable` and `Supplier<T>`, but declaring `throws Exception` on their single
abstract method. This lets you pass a lambda that throws a checked exception (`SQLException`, `IOException` etc.)
directly to `measureChecked` / `measureRepeatedlyChecked`, without forcing you to wrap it in a try/catch just to satisfy
the compiler.

> [!NOTE]
> Java's standard `Runnable` and `Supplier<T>` don't allow their functional methods to throw checked exceptions. This
> forces you to wrap every checked-exception-throwing lambda in an artificial try/catch cluttering your code with
> boilerplate. These variants remove that friction.

Without `CheckedRunnable` — forced wrapping, and the original type is lost

```java
Runnable r = () -> {
    try {
        // throws SQLException
        dbUtils.closeConnection();
    } catch (SQLException e) {
        // original type lost
        throw new RuntimeException(e);
    }
};
```

With `CheckedRunnable` — clean, direct, and original type preserved

```java
CheckedRunnable r = () -> dbUtils.closeConnection();
```

Without `CheckedSupplier` — forced wrapping, and the original type is lost

```java
Supplier<Connection> s = () -> {
    try {
        // throws SQLException
        return dbUtils.getConnection();
    } catch (SQLException e) {
        // original type lost
        throw new RuntimeException(e);
    }
};
```

With `CheckedSupplier` — clean, direct, and original type preserved

```java
CheckedSupplier<Connection> s = () -> dbUtils.getConnection();
```

> [!NOTE]
> `CheckedRunnable`/`CheckedSupplier<T>` do **not** extend `Runnable`/`Supplier<T>` — this isn't a design choice, it's a
> Java language rule. A subinterface can only narrow the checked exceptions an inherited method declares, never widen
> them. Since `Runnable.run()`/`Supplier<T>.get()` declare no checked exceptions at all, extending either and
> redeclaring the method with `throws Exception` simply doesn't compile. These are deliberately independent interfaces
> that happen to share a method shape — the same pattern the JDK itself uses for `Callable<V>`, which doesn't extend
> `Supplier<V>` for the identical reason.

---

## Design Decisions & Their Reasoning

This section documents *why* the library looks the way it does — including a few decisions that were taken initially but
later revisited and corrected along the way.

### Why `measure` / `measureChecked` instead of one overloaded method

Java cannot cleanly disambiguate `Runnable` from `CheckedRunnable` or `Supplier<T>` from `CheckedSupplier<T>` via
overloading when a lambda body doesn't declare any exception — the compiler reports an ambiguous method call.

If `measure` were overloaded to accept either type (`Runnable`/`CheckedRunnable`):

```java
public static TimedResult<Void> measure(final Runnable method) { ... }

public static TimedResult<Void> measure(final CheckedRunnable method) { ... }
```

A plain lambda with no checked exception satisfies both signatures equally:

```java
StopWatch.measure(() -> eventPublisher.publishEvent());
```

Neither `Runnable` nor `CheckedRunnable` is declared to throw anything here, so the compiler has no basis to pick one
over the other. It rejects the call outright:

IntelliJ reports:

```
Ambiguous method call. Both measure(Runnable) in StopWatch and measure(CheckedRunnable) in StopWatch match
```

The ambiguity *can* be worked around — by explicitly storing it in a variable or casting the lambda inline:

```java
// Compiles — r is explicitly typed as Runnable, so there's nothing to infer
Runnable r = () -> eventPublisher.publishEvent();
StopWatch.measure(r);
```

```java
// Compiles — the cast tells the compiler which overload to target
StopWatch.measure((Runnable) () -> eventPublisher.publishEvent());
```

The above workarounds require the *caller* to already know about the ambiguity and add extra syntax to resolve it every
single time they write a lambda with no checked exception. That's friction nobody should have to think about just to
call a timing method. Splitting them into two explicitly named methods removes the ambiguity entirely and makes the
exception-handling expectation visible at the call site, without the caller needing to know *why*.

The Solution:

```java
// plain lambda, no ceremony
StopWatch.measure(() -> eventPublisher.publishEvent());
```

```java
// checked exception, still no ceremony
StopWatch.measureChecked(() -> dbUtils.closeConnection());
```

The method names themselves disambiguate. The caller picks the method that matches their lambda; the compiler never has
to guess, and nobody has to write a cast just to time a method call.

**The same ambiguity applies to value-returning methods** — `Supplier<T>`/`CheckedSupplier<T>` face an identical
problem, for the same reason:

If `measure` were overloaded to accept either type (`Supplier`/`CheckedSupplier`):

```java
public static <T> TimedResult<T> measure(final Supplier<T> method) { ... }

public static <T> TimedResult<T> measure(final CheckedSupplier<T> method) { ... }
```

A plain lambda with no checked exception satisfies both signatures equally:

```java
StopWatch.measure(() -> userService.getUserById(101));
```

Neither `Supplier` nor `CheckedSupplier` is declared to throw anything here, so the compiler has no basis to pick one
over the other. It rejects the call outright:

IntelliJ reports:

```
Ambiguous method call. Both measure(Supplier) in StopWatch and measure(CheckedSupplier) in StopWatch match
```

The ambiguity *can* be worked around — by explicitly storing it in a variable or casting the lambda inline:

```java
// Compiles — s is explicitly typed as Supplier<User>, so there's nothing to infer
Supplier<User> s = () -> userService.getUserById(101);
StopWatch.measure(s);
```

```java
// Compiles — the cast tells the compiler which overload to target
StopWatch.measure((Supplier<User>) () -> userService.getUserById(101));
```

As explained earlier, the above workarounds are too much friction.

The Solution:

```java
// plain lambda, no ceremony
StopWatch.measure(() -> userService.getUserById(101));
```

```java
// checked exception, still no ceremony
StopWatch.measureChecked(() -> dbUtils.getConnection());
```

---

### Why `Runnable` / `CheckedRunnable` variants delegate to the `Supplier` variants internally

Rather than duplicating the timing loop for void methods, `measure(Runnable)` and `measureRepeatedly(Runnable, ...)`
wrap their input as a `Supplier<Void>` / `CheckedSupplier<Void>` that runs the `Runnable` and then returns null,
delegating to the value-returning variant.

```java
public static TimedResult<Void> measure(Runnable method) {
    return measure(() -> {
        method.run();
        return null;
    });
}
```

```java
public static TimingStatistics measureRepeatedly(Runnable method, int iterations, int warmupIterations) {
    return measureRepeatedly(() -> {
        method.run();
        return null;
    }, iterations, warmupIterations);
}
```

This means one implementation handles the actual timing logic, with no risk of the `Runnable` and `Supplier` variants
drifting apart or behaving differently over time. It also means every guarantee documented for the `Supplier` variants —
warmup handling, failure tracking, exception behavior — applies identically to the `Runnable` variants, since under the
hood, they're running the exact same code.

---

### `TimedResult`: nanos internally, millis on retrieval

Elapsed time is captured via `System.nanoTime()` and stored as-is. Millisecond conversion happens only when
`getElapsedMillis()` is called, via `TimeUnit.NANOSECONDS.toMillis()` — never by manual division. This keeps the
conversion consistent everywhere it's used and avoids subtle rounding bugs from repeated division.

---

### `TimingStatistics`: built on `LongSummaryStatistics`

Total, average, min, and max are delegated entirely to `java.util.LongSummaryStatistics` — a battle-tested JDK class. No
custom arithmetic is performed for these core statistics, eliminating an entire category of off-by-one or precision
bugs.

---

### The big correction: failure timings do **not** belong in performance statistics

An earlier design included failed-iteration timings directly in the statistics, on the reasoning that *"a slow failure
is as real a data point as a slow success."* That reasoning sounds correct in isolation — but it doesn't survive the
question **"how would a caller actually use that number?"** in a repeatedly measured method.

In practice, the elapsed time until an exception is thrown depends entirely on **where** in the method the failure
occurred. An instant validation failure and a 30-second connection timeout failure are both "failures" — but their
timings mean completely different things and cannot be meaningfully averaged together. Mixing them into
`getAverageMillis()` contaminates the picture of how the method performs *when it works*, without providing any
actionable insight in return — the exception type and message already convey far more about *what* went wrong than its
timing does.

> [!IMPORTANT]
> **The resolution:** statistics (`LongSummaryStatistics` and all derived accessors) cover
> **successful iterations only**. Failures are represented separately and explicitly via `hasFailures()`,
> `getFailedIterations()`, and `getLastException()`.

---

### The second correction: "always return, never throw"

The original design for `measureRepeatedly` re-threw the *last* exception after the loop completed if any iteration had
failed. This created a contradiction: if even one out of a thousand iterations failed, the caller received an exception
and **lost the statistics for the other 999** — directly undermining the goal of returning useful aggregated data.

> [!IMPORTANT]
> **The resolution:** `measureRepeatedly` and `measureRepeatedlyChecked` *always* return a `TimingStatistics`. The
> last exception (if any) is captured internally and surfaced (not rethrown) via `getLastException()` for the caller to
> inspect. As a direct consequence, `measureRepeatedlyChecked` no longer declares `throws Exception` — it has nothing
> left to throw.

---

### Warmup iterations are a first-class concept, not an afterthought

JIT compilation and class loading make the first few invocations of *any* method artificially slow — this has nothing to
do with the method's real performance. Rather than asking callers to discard early results manually, `warmupIterations`
is a required parameter: those invocations execute (with real side effects) but are excluded from every statistic.

---

### `StopWatch` is static; `TimingLogger` is an instance — and that's correct

`StopWatch` is a stateless utility — every method receives everything it needs as parameters and returns a result. This
is the same shape as `java.util.Arrays` or `java.util.Collections`, and a private constructor enforces it cannot be
instantiated.

`TimingLogger`, by contrast, **must** carry state — specifically, the start time captured when `start()` is called —
across to `close()`, which may happen much later in a different point of the method body. `AutoCloseable` requires an
instance with a lifecycle; there is no honest way to make this static. Each class has the shape its responsibilities
demand, rather than being forced into uniformity for its own sake.

---

### `TimingLogger`'s threshold: `0` means "no threshold," and `< 0` is invalid

`NO_THRESHOLD = 0L` is a deliberate, user-passable sentinel — calling `start(label, logger, 0)` is equivalent to calling
the no-threshold overload `start(label, logger)`. Validation rejects only **negative** values (`slowThresholdMillis <
0`), not zero, because zero has a legitimate meaning here rather than being a degenerate edge case to reject.

---

## ✅ Best Practices

- ✅ **Use `StopWatch` for investigations, benchmarks, and one-off questions.** It is **not** intended to live
  permanently in production hot paths — use `TimingLogger` for those use cases.

- ✅ **Always give `measureRepeatedly` a meaningful warmup count.** A handful of warmup iterations (5–10 is often enough)
  prevent JIT warm-up from dominating your results, especially for short-running methods.

- ✅ **Inspect failures, don't catch them.** Even though the method you pass in may throw, `measureRepeatedly` and
  `measureRepeatedlyChecked` catch it internally and never propagate it — the call itself never throws. Check
  `hasFailures()` and `getLastException()` after the call instead of wrapping it in try/catch, which would be
  unreachable dead code.

- ✅ **Pass your own class's logger to `TimingLogger`.** This keeps timing lines filterable and contextual alongside your
  other log output — never share a single logger across unrelated classes.

- ✅ **Set slow thresholds based on real SLAs, not guesses.** A threshold that fires constantly is noise; a threshold
  that never fires provides no value. Tune it against your actual latency expectations.

- ✅ **Don't mix `measure` and `measureChecked` mentally — let the compiler guide you.** If your lambda doesn't compile
  under `measure`, that's the signal to use `measureChecked` instead, not to wrap the exception yourself.

---

## Testing Philosophy

Tests are organized by method variant (`measure`, `measureChecked`, `measureRepeatedly`, `measureRepeatedlyChecked`)
and, within each, by overload (`Supplier` vs `Runnable`) — covering delegation-specific concerns such as whether each
variant correctly propagates or catches exceptions, counts iterations, and rejects nulls.

Statistical computation facts (min/max/average/total, millis-nanos consistency) are verified once rather than duplicated
across every repeated-measurement variant — all four converge on the same underlying computation, so repeating those
assertions per variant wouldn't add real confidence.

---

## Implementation Status & Roadmap

This project grows feature by feature — each one fully discussed and designed before it's built. This section reflects
that: a flat list of what exists, an ordered list of what's committed to next, and an open-ended list of ideas that have
been discussed or being discussed, but not yet promised.

### ✅ Implemented

- Single measurement — `measure`, `measureChecked`

- Repeated measurement — `measureRepeatedly`, `measureRepeatedlyChecked`

- Failure tracking — `hasFailures()`, `getLastException()`

- Ambient production timing — `TimingLogger`

### 🚀 Roadmap

Committed next steps, in order:

1. Head-to-head comparison — `compare`, `compareChecked` methods returning a `ComparisonResult`

2. `Candidate` based comparison API — replaces the six-argument `compare` signature with a paired label-and-method input

3. Percentile statistics — `getPercentileMillis()` for P50 / P75 / P95 / P99 on `TimingStatistics`

### 🌟 Future Considerations

Ideas raised during design discussions, not yet committed to. Some may be implemented, refined, or set aside as the
library matures:

- **`toMap()` on `ComparisonResult`** — a flat `Map<String, Object>` representation for external consumption (metrics
  platforms, structured logging, MDC) without coupling the library to a JSON dependency

- **MDC integration for `TimingLogger`** — attaching elapsed time to the logging context rather than (or in addition to)
  a log line; depends on the consuming application's log-aggregation stack

- **Spring AOP `@Timed` module** — an optional, separate module providing an annotation-based alternative to
  `TimingLogger` for Spring Boot consumers, without adding a Spring dependency to the core library

#### ❌ Considered and rejected

- **Checkpoint / `CheckpointTimer`** — a mechanism for recording multiple named splits within a single timed block.
  Rejected as too invasive: it would require call sites to pass a checkpoint object through their method body,
  contradicting the "no side effects, no restructuring required" principle that both `StopWatch` and `TimingLogger` are
  built on.

---

<div align="center">

📌 *This README is a living document and will be updated as each feature above is implemented and committed.*

</div>
