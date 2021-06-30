[![](https://jitpack.io/v/segmentio/analytics-kotlin.svg)](https://jitpack.io/#segmentio/analytics-kotlin)
# Analytics-Kotlin
The hassle-free way to add Segment analytics to your kotlin app (Android/JVM).

NOTE: This project is currently in the Pilot phase and is covered by Segment's [First Access & Beta Preview Terms](https://segment.com/legal/first-access-beta-preview/).  We encourage you
to try out this new library. Please provide feedback via Github issues/PRs, and feel free to submit pull requests.  This library will eventually 
supplant our `analytics-android` library.

## Table of Contents
- [Installation](#installation)
- [Usage](#usage)
    - [Setting up the client](#setting-up-the-client)
    - [Client Options](#client-options)
- [Client Methods](#client-methods)
    - track
    - identify
    - screen
    - group
    - add
    - find
    - remove
    - flush
- [Plugin Architecture](#plugin-architecture)
    - [Fundamentals](#fundamentals)
    - [Advanced Concepts](#advanced-concepts)
- [Contributing](#contributing)
- [Code of Conduct](#code-of-conduct)
- [License](#license)

## Installation
For our pilot phase, we will be using [jitpack](https://jitpack.io/#segmentio/analytics-kotlin) to distribute the library
<details open>
<summary>Android</summary>
<br>
In your app's build.gradle file add the following

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.segmentio.analytics-kotlin:android:+'
}
```

</details>

<details>
<summary>JVM</summary>
<br>

In your app's build.gradle file add the following
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.segmentio.analytics-kotlin:core:+'
}
```
</details>

### Permissions
Ensure that you add these permissions to your `AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
```

## Usage
### Setting up the client
Our library provides multiple initialisation techniques for the analytics client. The analytics client manages all your tracking events.

Android
```kotlin
Analytics("SEGMENT_API_KEY", applicationContext, applicationCoroutineScope)
```

Generic
```kotlin
Analytics("SEGMENT_API_KEY")
```

### Client Options
When creating a new client, you can pass several [options](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/Configuration.kt) which can be found below.

Android
```kotlin
Analytics("SEGMENT_API_KEY", applicationContext) {
    analyticsScope = applicationCoroutineScope
    collectDeviceId = true
    recordScreenViews = true
    trackApplicationLifecycleEvents = true
    trackDeepLinks = true
    flushAt = 1
    //...other config options
}
```

JVM
```kotlin
Analytics("SEGMENT_API_KEY") {
    collectDeviceId = true
    recordScreenViews = true
    trackApplicationLifecycleEvents = true
    trackDeepLinks = true
    flushAt = 1
    //...other config options
}
// OR
Analytics(Configuration (
    writeKey = "123",
    collectDeviceId = true,
    recordScreenViews = true,
    trackApplicationLifecycleEvents = true,
    trackDeepLinks = true,
    flushAt = 1,
    //...other config options
))
```

| Name |  Default | Description |
| ---- |  ------- | ----- |
| writeKey | *required* |  Your Segment writeKey |
| application | `null` |  application specific object (in case of Android: ApplicationContext) |
| analyticsScope | `MainScope()` |  CoroutineScope on which all analytics coroutines will run |
| analyticsDispatcher | `Executors.newSingleThreadExecutor()` |  Dispatcher running analytics tasks |
| ioDispatcher | `Dispatchers.IO` |  Dispatcher running IO tasks |
| storageProvider | `ConcreteStorageProvider` |  Provider for storage class, generally do not want to modify |
| collectDeviceId | `false` |  automatically collect deviceId |
| recordScreenViews | `false` |  automatically trigger screen events on Activity Start |
| trackApplicationLifecycleEvents | `false` |  automatically track Lifecycle events |
| trackDeepLinks | `false` |  automatically track [Deep link](https://developer.android.com/training/app-links/deep-linking) opened based on intents |
| useLifecycleObserver | `false` |  enables the use of LifecycleObserver to track Application lifecycle events |
| flushAt | `20` |  count of events at which we flush events
| flushInterval | `30` (seconds) |  interval in seconds at which we flush events
| defaultSettings | `{}` |  Settings object that will be used as fallback in case of network failure
| autoAddSegmentDestination | `true` |  automatically add SegmentDestination plugin, disable in case you want to add plugins to SegmentDestination
| apiHost | `api.segment.io/v1` |  set a default apiHost to which Segment sends event

## Client Methods

### track
The track method is how you record any actions your users perform, along with any properties that describe the action.

Method signature:
```kotlin
fun track(name: String, properties: JsonObject = emptyJsonObject)

// If <T> is annotated with @Serializable you will not need to provide a serializationStrategy
fun <T> track(name: String, properties: T, serializationStrategy: KSerializer<T>)
```

Example usage:
```kotlin
analytics.track("View Product", buildJsonObject {
    put("productId", 123)
    put("productName" "Striped trousers")
});
```

### identify
The identify call lets you tie a user to their actions and record traits about them. This includes a unique user ID and any optional traits you know about them like their email, name, etc. The traits option can include any information you might want to tie to the user, but when using any of the reserved user traits, you should make sure to only use them for their intended meaning.

Method signature:
```kotlin
fun identify(userId: String, traits: JsonObject = emptyJsonObject)

// If <T> is annotated with @Serializable you will not need to provide a serializationStrategy
fun <T> identify(userId: String, traits: T, serializationStrategy: KSerializer<T>)
```

Example Usage:
```kotlin
analytics.identify("user-123", buildJsonObject {
    put("username", "MisterWhiskers")
    put("email", "hello@test.com")
    put("plan", "premium")
});
```

### screen
The screen call lets you record whenever a user sees a screen in your mobile app, along with any properties about the screen.

Method signature:
```kotlin
fun screen(screenTitle: String, properties: JsonObject = emptyJsonObject, category: String = "")

// If <T> is annotated with @Serializable you will not need to provide a serializationStrategy
fun <T> screen(screenTitle: String, properties: T, category: String = "", serializationStrategy: KSerializer<T>)
```

Example Usage:
```kotlin
analytics.screen("ScreenName", buildJsonObject {
    put("productSlug", "example-product-123")
});
```
You can enable automatic screen tracking using the Config option `autoRecordScreenViews = true`

### group
The group API call is how you associate an individual user with a group—be it a company, organization, account, project, team or whatever other crazy name you came up with for the same concept! This includes a unique group ID and any optional group traits you know about them like the company name industry, number of employees, etc. The traits option can include any information you might want to tie to the group, but when using any of the reserved group traits, you should make sure to only use them for their intended meaning.
Method signature:
```kotlin
fun group(groupId: String, traits: JsonObject = emptyJsonObject)

// If <T> is annotated with @Serializable you will not need to provide a serializationStrategy
fun <T> group(groupId: String, traits: T, serializationStrategy: KSerializer<T>)
```

Example Usage:
```kotlin
analytics.group("user-123", buildJsonObject {
    put("username", "MisterWhiskers")
    put("email", "hello@test.com")
    put("plan", "premium")
});
```

### add
add API allows you to add a plugin to the analytics timeline

Method signature:
```kotlin
fun add(plugin: Plugin): Analytics
```

Example Usage:
```kotlin
val plugin = object: Plugin {
    override val type = Plugin.Type.Enrichment
    override val name = "SomePlugin"
    override var lateinit analytics: Analytics
}
analytics.add(plugin)
```

### find
find a registered plugin from the analytics timeline

Method signature:
```kotlin
fun find(pluginName: String): Plugin
```

Example Usage:
```kotlin
val plugin = analytics.find("SomePlugin")
```

### remove
remove a registered plugin from the analytics timeline

Method signature:
```kotlin
fun remove(pluginName: String): Analytics
```

Example Usage:
```kotlin
analytics.remove("SomePlugin")
```

### flush

## Plugin Architecture
Our new plugin architecture enables you to modify/augment how the analytics client works completely. From modifying event payloads to changing analytics functionality, Plugins are the easiest way to get things done
Plugins are run through a timeline, which executes plugins in order of insertion based on their types.
We have the following [types]
- `Before` _Executed before event processing begins_
- `Enrichment` _Executed as the first level of event processing_
- `Destination` _Executed as events begin to pass off to destinations_
- `After` _Executed after all event processing is completed.  This can be used to perform cleanup operations, etc_
- `Utility` _Executed only when called manually, such as Logging_

### Fundamentals
We have 3 types of basic plugins that you can use as a foundation for modifying functionality

- [`Plugin`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt)
The most trivial plugin interface that will act on any event payload going through the timeline.
For example if you wanted to add something to the context object of any event payload as an enrichment.
```kotlin
class SomePlugin: Plugin {
    override val type = Plugin.Type.Enrichment
    override val name = "SomePlugin"

    override var lateinit analytics: Analytics

    override fun execute(event: BaseEvent): BaseEvent? {
        event.putInContext("foo", "bar")
        return event
    }
}
```

- [`EventPlugin`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt)
A plugin interface that will act only on specific event types. You can choose the event types by only overriding the event functions you want.
For example if you only wanted to act on `track` & `identify` events
```kotlin
class SomePlugin: EventPlugin {
    override fun track(event: TrackEvent): BaseEvent? {
        // code to modify track event
        return event
    }
    override fun identify(event: TrackEvent): BaseEvent? {
        // code to modify identify event
        return event
    }
}
```

- [`DestinationPlugin`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt)
A plugin interface most commonly used for device-mode destinations. This plugin contains an internal timeline that follows the same process as the analytics timeline,
allowing you to modify/augment how events reach the particular destination.
For example if you wanted to implement a device-mode destination plugin for Amplitude
```kotlin
class AmplitudePlugin: DestinationPlugin() {
    override val name = "Amplitude"

    val amplitudeSDK: Amplitude

    init {
        amplitudeSDK = Amplitude.instance
        amplitudeSDK.initialize(applicationContext, "API_KEY");
    }

    override fun track(event: TrackEvent): BaseEvent? {
        amplitudeSDK.logEvent(event.name)
        return event
    }
}
```


### Advanced concepts
- [`setup(Analytics)`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt#L20-L24)
Use this function to setup your plugin. This will be implicitly called once the plugin is registered
- [`update(Settings)`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt#L31-L33)
Use this function to react to any settings updates. This will be implicitly called when settings are updated.
You can force a settings update by calling `analytics.checkSettings()`
- AndroidLifecycle hooks
Plugins can also hook into [`AndroidLifecycle`]() functions by implementing an interface. These functions will get called implicitly as the lifecycle events are processed.
- `DestinationPlugin` timeline
The destination plugin contains an internal timeline that follows the same process as the analytics timeline, allowing you to modify/augment how events reach the particular destination.
For example if you only wanted to add a context key when sending an event to `Amplitude`
```kotlin
val amplitudePlugin = AmplitudePlugin()
analytics.add(amplitudePlugin) // add amplitudePlugin to the analytics client

val amplitudeEnrichment = object: Plugin {
    override val type = Plugin.Type.Enrichment
    override val name = "SomePlugin"

    override var lateinit analytics: Analytics

    override fun execute(event: BaseEvent): BaseEvent? {
        event.putInContext("foo", "bar")
        return event
    }
}

amplitudePlugin.add(amplitudeEnrichment) // add enrichment plugin to amplitude timeline
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

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
