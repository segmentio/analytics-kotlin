# Analytics-Kotlin Java Compatibility 
[![](https://jitpack.io/v/segmentio/analytics-kotlin.svg)](https://jitpack.io/#segmentio/analytics-kotlin)

The hassle-free way to add Segment analytics to your kotlin app (Android/JVM).

NOTE: This project is currently in the Pilot phase and is covered by Segment's [First Access & Beta Preview Terms](https://segment.com/legal/first-access-beta-preview/).  We encourage you
to try out this new library. Please provide feedback via Github issues/PRs, and feel free to submit pull requests.  This library will eventually 
supplant our `analytics-android` library.

NOTE: This document serves as an explanation of usage of this `analytics-kotlin` library for pure Java codebase. For the sample usages in Kotlin and more detailed architectural explanation, please refer to our main [README.md doc](README.md).

## Table of Contents
- [Analytics-Kotlin Java Compatibility](#analytics-kotlin-java-compatibility)
  - [Table of Contents](#table-of-contents)
  - [Installation](#installation)
    - [Permissions](#permissions)
  - [Usage](#usage)
    - [Setting up the client](#setting-up-the-client)
    - [Client Options](#client-options)
  - [Client Methods](#client-methods)
    - [track](#track)
    - [identify](#identify)
    - [screen](#screen)
    - [group](#group)
    - [alias](#alias)
    - [add](#add)
    - [find](#find)
    - [remove](#remove)
    - [flush](#flush)
  - [Plugin Architecture](#plugin-architecture)
    - [Fundamentals](#fundamentals)
    - [Advanced concepts](#advanced-concepts)
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

**Android**
```java
Analytics analytics = AndroidAnalyticsKt.Analytics(BuildConfig.SEGMENT_WRITE_KEY, getApplicationContext(), configuration -> Unit.INSTANCE);
```

However, if you are in pure Java codebase, some of the functions in `Analytics` is not compatible. We provide a wrapper class `JavaAnalytics` to make all the functions available to Java.
```java
JavaAnalytics analyticsCompat = new JavaAnalytics(analytics);
```

**Generic**
```java
JavaAnalytics analytics = new JavaAnalytics(
    new ConfigurationBuilder(BuildConfig.SEGMENT_WRITE_KEY).build()
);
```

Here, `ConfigurationBuilder` implements a builder pattern to help instantiates a `Configuration` object in Java, it can also be used in Android project.

### Client Options
When creating a new client, you can pass several [options](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/Configuration.kt) which can be found below.

**Android**
```java
AndroidAnalyticsKt.Analytics(BuildConfig.SEGMENT_WRITE_KEY, getApplicationContext(), configuration -> {
    
    configuration.setFlushAt(1);
    configuration.setCollectDeviceId(true);
    configuration.setTrackApplicationLifecycleEvents(true);
    configuration.setTrackDeepLinks(true);
    //...other config options

    return Unit.INSTANCE;
});
```

**JVM**
```java
JavaAnalytics analytics = new JavaAnalytics(
    new ConfigurationBuilder(BuildConfig.SEGMENT_WRITE_KEY)
        .setFlushAt(10)
        .setCollectDeviceId(true)
        .setTrackApplicationLifecycleEvents(true)
        .setTrackDeepLinks(true)    //...other config options
        .build()
);
```


## Client Methods

### track
The track method is how you record any actions your users perform, along with any properties that describe the action.

Example usage:
```java
analytics.track("View Product", Builders.buildJsonObject(o -> {
    o.put("productId", 123)
        .put("productName", "Striped trousers");
}));
```

Here `Builders` is a utility class that has a series of convinent functions that helps build a Kotlin JsonObject.

You can also let your classes implement `JsonSerializable` to avoid buiding up a `JsonObject` every time.
```java
class YourJsonSerializable implements JsonSerializable {
    public JsonObject serialize() {
        return Builders.buildJsonObject(o -> {
            o.put("productId", 123)
                .put("productName", "Striped trousers");
        }));
    }
}

analytics.track("View Product", new YourJsonSerializable());
```

For implementations supporting a minimum API version below 24, the `buildJsonObjectFunc` fucntion will need to be used.
```java
analytics.track("View Product", Builders.buildJsonObjectFunc(o -> {
    o.put("productId", 123)
        .put("productName", "Striped trousers");
}));
```

### identify
The identify call lets you tie a user to their actions and record traits about them. This includes a unique user ID and any optional traits you know about them like their email, name, etc. The traits option can include any information you might want to tie to the user, but when using any of the reserved user traits, you should make sure to only use them for their intended meaning.

Example Usage:
```java
analytics.identify("user-123", Builders.buildJsonObject(o -> {
    o.put("username", "MisterWhiskers")
        .put("email", "hello@test.com")
        .put("plan", "premium");
}));

// or

analytics.identify("user-123", new YourJsonSerializable());
```

### screen
The screen call lets you record whenever a user sees a screen in your mobile app, along with any properties about the screen.

Example Usage:
```java
analytics.screen("ScreenName", Builders.buildJsonObject(o -> {
    o.put("productSlug", "example-product-123");
}));

// or

analytics.screen("ScreenName", new YourJsonSerializable());
```
You can also opt-into auto screen tracking by adding this [plugin](https://github.com/segmentio/analytics-kotlin/blob/main/samples/kotlin-android-app/src/main/java/com/segment/analytics/next/plugins/AndroidRecordScreenPlugin.kt)

### group
The group API call is how you associate an individual user with a group—be it a company, organization, account, project, team or whatever other crazy name you came up with for the same concept! This includes a unique group ID and any optional group traits you know about them like the company name industry, number of employees, etc. The traits option can include any information you might want to tie to the group, but when using any of the reserved group traits, you should make sure to only use them for their intended meaning.

Example Usage:
```java
analytics.group("user-123", Builders.buildJsonObject(o -> {
    o.put("username", "MisterWhiskers")
        .put("email", "hello@test.com")
        .put("plan", "premium");
}));

// or

analytics.group("user-123", new YourJsonSerializable());
```

### alias

The alias method is used to merge two user identities, effectively connecting two sets of user data as one. This is an advanced method, but it is required to manage user identities successfully in some of our integrations.
Method signature:
```kotlin
fun alias(newId: String)
```

Example Usage:
```kotlin
analytics.alias("newId")
```

### add
add API allows you to add a plugin to the analytics timeline

Example Usage:
```java
analytics.add(new SomePlugin());
```

`SomePlugin` is your customized `Plugin`. See [Plugin Architecture](#plugin-architecture) for details of plugin customization

### find
find a registered plugin from the analytics timeline

Example Usage:
```java
SomePlugin somePlugin = analytics.find(SomePlugin.class);
```

### remove
remove a registered plugin from the analytics timeline

Example Usage:
```kotlin
analytics.remove(somePlugin);
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
```java
class SomePlugin implements Plugin {

    private Analytics analytics;

    @Override
    public void update(@NonNull Settings settings, @NonNull UpdateType type) {
        // stub method
    }

    @Nullable
    @Override
    public BaseEvent execute(@NonNull BaseEvent event) {
        EventTransformer.putInContext(event, "foo", "bar");
        return event;
    }

    @Override
    public void setup(@NonNull Analytics analytics) {
        this.analytics = analytics;
    }

    @NonNull
    @Override
    public Plugin.Type getType() {
        return Plugin.Type.Enrichment;
    }

    @NonNull
    @Override
    public Analytics getAnalytics() {
        return analytics;
    }

    @Override
    public void setAnalytics(@NonNull Analytics analytics) {
        setup(analytics);
    }
}
```

- [`EventPlugin`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt)
A plugin interface that will act only on specific event types. 


```java
static class SomePlugin implements EventPlugin {

    //...Plugin methods
    
    @Nullable
    @Override
    public BaseEvent track(@NonNull TrackEvent payload) {
        // code to modify track event
        return payload;
    }

    @Nullable
    @Override
    public BaseEvent identify(@NonNull IdentifyEvent payload) {
        // code to modify identify event
        return playload;
    }

    @Nullable
    @Override
    public BaseEvent screen(@NonNull ScreenEvent payload) {
        // code to modify screen event
        return payload;
    }

    @Nullable
    @Override
    public BaseEvent group(@NonNull GroupEvent payload) {
        // code to modify group event
        return payload;
    }

    @Nullable
    @Override
    public BaseEvent alias(@NonNull AliasEvent payload) {
        // code to modify alias event
        return payload;
    }
}
```

- [`DestinationPlugin`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt)
A plugin interface most commonly used for device-mode destinations. This plugin contains an internal timeline that follows the same process as the analytics timeline,
allowing you to modify/augment how events reach the particular destination.
For example if you wanted to implement a device-mode destination plugin for Amplitude
```java
static class AmplitudePlugin extends DestinationPlugin {

    private Amplitude amplitudeSDK;

    public AmplitudePlugin() {
        amplitudeSDK = Amplitude.getInstance();
        amplitudeSDK.initialize(applicationContext, "API_KEY");
    }

    @Nullable
    @Override
    public BaseEvent track(@NonNull TrackEvent payload) {
        // code to react to event with amplitudeSDK
        return payload;
    }

    @Nullable
    @Override
    public BaseEvent identify(@NonNull IdentifyEvent payload) {
        // code to react to event with amplitudeSDK
        return payload;
    }

    @Nullable
    @Override
    public BaseEvent screen(@NonNull ScreenEvent payload) {
        // code to react to event with amplitudeSDK
        return payload;
    }

    @Nullable
    @Override
    public BaseEvent group(@NonNull GroupEvent payload) {
        // code to react to event with amplitudeSDK
        return payload;
    }

    @Nullable
    @Override
    public BaseEvent alias(@NonNull AliasEvent payload) {
        // code to react to event with amplitudeSDK
        return payload;
    }

    @NonNull
    @Override
    public String getKey() {
        return "Amplitude";
    }
}
```


### Advanced concepts
- [`setup(Analytics)`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt#L20-L24)
Use this function to setup your plugin. This will be implicitly called once the plugin is registered
- [`update(Settings, UpdateType)`](https://github.com/segmentio/analytics-kotlin/blob/main/analytics-kotlin/src/main/java/com/segment/analytics/platform/Plugin.kt#L31-L33)
Use this function to react to any settings updates. This will be implicitly called when settings are updated. The `UpdateType` is used to indicate whether the settings change
is for initialization or refreshment, and you can use it to decide whether to update your plugins accordingly.
You can force a settings update by calling `analytics.checkSettings()`
- AndroidLifecycle hooks
Plugins can also hook into [`AndroidLifecycle`]() functions by implementing an interface. These functions will get called implicitly as the lifecycle events are processed.
- `DestinationPlugin` timeline
The destination plugin contains an internal timeline that follows the same process as the analytics timeline, allowing you to modify/augment how events reach the particular destination.

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
