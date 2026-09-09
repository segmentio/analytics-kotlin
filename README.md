# Analytics-Kotlin
[![maven](https://img.shields.io/maven-central/v/com.segment.analytics.kotlin/android)](https://repo1.maven.org/maven2/com/segment/analytics/kotlin/)
[![](https://github.com/segmentio/analytics-kotlin/actions/workflows/build.yml/badge.svg)](https://github.com/segmentio/analytics-kotlin/actions)
[![codecov](https://codecov.io/gh/segmentio/analytics-kotlin/branch/main/graph/badge.svg?token=U5FDRBZOXO)](https://codecov.io/gh/segmentio/analytics-kotlin)
[![Known Vulnerabilities](https://snyk.io/test/github/segmentio/analytics-kotlin/badge.svg)](https://snyk.io/test/github/segmentio/analytics-kotlin)
[![](https://img.shields.io/github/license/segmentio/analytics-kotlin)](https://github.com/segmentio/analytics-kotlin/blob/main/LICENSE)

### 🎉 Flagship 🎉
This library is one of Segment’s most popular Flagship libraries. It is actively maintained by Segment, benefitting from new feature releases and ongoing support.

The hassle-free way to add Segment analytics to your kotlin app (Android/JVM). Analytics helps you measure your users, product, and business. It unlocks insights into your app's funnel, core business metrics, and whether you have product-market fit.

## How to get started
1. **Collect analytics data** from your app(s).
  - The top 200 Segment companies collect data from 5+ source types (web, mobile, server, CRM, etc.).
2. **Send the data to analytics tools** (for example, Google Analytics, Amplitude, Mixpanel).
  - Over 250+ Segment companies send data to eight categories of destinations such as analytics tools, warehouses, email marketing and remarketing systems, session recording, and more.
3. **Explore your data** by creating metrics (for example, new signups, retention cohorts, and revenue generation).
  - The best Segment companies use retention cohorts to measure product market fit. Netflix has 70% paid retention after 12 months, 30% after 7 years.

[Segment](https://segment.com) collects analytics data and allows you to send it to more than 250 apps (such as Google Analytics, Mixpanel, Optimizely, Facebook Ads, Slack, Sentry) just by flipping a switch. You only need one Segment code snippet, and you can turn integrations on and off at will, with no additional code. [Sign up with Segment today](https://app.segment.com/signup).

### Why?
1. **Power all your analytics apps with the same data**. Instead of writing code to integrate all of your tools individually, send data to Segment, once.

2. **Install tracking for the last time**. We're the last integration you'll ever need to write. You only need to instrument Segment once. Reduce all of your tracking code and advertising tags into a single set of API calls.

3. **Send data from anywhere**. Send Segment data from any device, and we'll transform and send it on to any tool.

4. **Query your data in SQL**. Slice, dice, and analyze your data in detail with Segment SQL. We'll transform and load your customer behavioral data directly from your apps into Amazon Redshift, Google BigQuery, or Postgres. Save weeks of engineering time by not having to invent your own data warehouse and ETL pipeline.

   For example, you can capture data on any app:
    ```kotlin
    @Serializable
    data class TrackProperties(
        var price: Double
    )

    // ...

    analytics.track('Order Completed', TrackProperties(price = 99.84))
    ```
   Then, query the resulting data in SQL:
    ```sql
    select * from app.order_completed
    order by price desc
    ```

## Documentation

You can find usage documentation at [https://segment.com/docs/sources/mobile/kotlin-android/](https://segment.com/docs/sources/mobile/kotlin-android/).

Explore more via the [example projects](samples) which showcase analytics instrumentation on different platforms/languages and usage of plugins. These projects contain sample [plugins](samples/kotlin-android-app/src/main/java/com/segment/analytics/next/plugins) and [destination plugins](samples/kotlin-android-app-destinations/src/main/java/com/segment/analytics/destinations/plugins) 

## Storage and security

### Write keys are not secrets

A Segment write key is a write-only ingestion identifier, not a credential. It grants no read access, no authentication, and no dashboard access — Segment intentionally ships write keys in client-side bundles (analytics.js, mobile app binaries). Treat it as an identifier that may be visible, not as a secret to be protected.

### JVM / server storage behavior

On the JVM (server) target, the default storage provider is `ConcreteStorageProvider`. It persists data to disk at:

```
/tmp/analytics-kotlin/<writeKey>/
├── analytics-kotlin-<writeKey>.properties   # userId, anonymousId, traits, settings
└── events/                                  # queued event batches awaiting flush
```

Two things to be aware of:

* **The path is not configurable** and includes the write key as a directory name.
* **Permissions are umask-derived.** These files are created without explicit POSIX permissions, so on a typical server umask (`0022`) they are world-readable (`0755` directories, `0644` files).

The `.properties` file and queued event batches contain whatever identity data your application sends — `userId`, `anonymousId`, and `identify` traits. If those traits include end-user PII, that PII is written to this location in cleartext until the events are flushed.

This applies **only to the JVM target**. Android uses `Context.MODE_PRIVATE`.

### Recommendation: do not use the default provider on shared hosts

Avoid the default `ConcreteStorageProvider` on shared, multi-tenant, or otherwise non-isolated hosts — for example a CI runner shared across jobs or tenants, a shared application server or jump box, or a container running more than one workload. Any other local user on such a host can read `/tmp/analytics-kotlin`. Because the parent directory name is fixed and contains no secret, `/tmp`'s sticky bit does not prevent a local user from pre-creating it (including as a symlink) before your application first runs.

Storage is fully user-controllable via `Configuration.storageProvider`.

**No disk persistence** — use the built-in in-memory provider. Events are queued in memory only, which eliminates on-disk exposure entirely. Note that queued events are lost if the process exits before a flush.

```kotlin
import com.segment.analytics.kotlin.core.utilities.InMemoryStorageProvider

val analytics = Analytics("<WRITE_KEY>") {
    storageProvider = InMemoryStorageProvider()
}
```

**Durable queuing with private storage** — implement the `StorageProvider` interface to write to a location and permission set you control (for example a user-private or encrypted directory with owner-only `0600` permissions).

```kotlin
object MyStorageProvider : StorageProvider {
    override fun createStorage(vararg params: Any): Storage {
        // return your own Storage implementation
    }
}

val analytics = Analytics("<WRITE_KEY>") {
    storageProvider = MyStorageProvider
}
```

**Restricting permissions on the default provider** — if you keep the default provider, run the JVM process with a restrictive umask so the storage tree is created owner-only:

```sh
umask 0077   # directories 0700, files 0600
```

Two caveats: a umask applies only to files created after it is set, so it will not tighten a storage directory that already exists; and it does not prevent the pre-creation issue described above, since that depends on the fixed directory path rather than on file permissions.

## Supported Device Mode Destinations

| Partner | Package |
| --- | --- |
| Amplitude | https://github.com/segment-integrations/analytics-kotlin-amplitude |
| AppsFlyer | https://github.com/segment-integrations/analytics-kotlin-appsflyer |
| Braze    | https://github.com/braze-inc/braze-segment-kotlin |
| Firebase | https://github.com/segment-integrations/analytics-kotlin-firebase |
| Mixpanel | https://github.com/segment-integrations/analytics-kotlin-mixpanel |
| Intercom | https://github.com/segment-integrations/analytics-kotlin-intercom |
| ComScore | https://github.com/segment-integrations/analytics-kotlin-comscore |


## Compatibility

* If you use pure Java codebase, please refer to [Java Compatibility](JAVA_COMPAT.md) for sample usages.
* If you use the SDK version prior to `1.10.4`, the SDK internally uses a number of Java 8 language API through desugaring (see [Java 8+ API desugaring support](https://developer.android.com/studio/write/java8-support#library-desugaring)). Please make sure your project:
  * either using Android Gradle plugin 4.0.0 or higher
  * or requiring a minimum API level of 26.
  * or upgrade the SDK version to latest


## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## Integrating with Segment

Interested in integrating your service with us? Check out our [Partners page](https://segment.com/partners/) for more details.

## Code of Conduct

Before contributing, please also see our [code of conduct](CODE_OF_CONDUCT.md).

## License
```
MIT License

Copyright (c) 2021 Segment

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
