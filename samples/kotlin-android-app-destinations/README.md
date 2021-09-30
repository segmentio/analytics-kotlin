# Sample App
This is a sample android app that uses the `analytics-kotlin` library and the new `Plugins` concepts. It is meant to be simplistic, and easy to understand all the while showcasing the power of the analytics-kotlin library

## Plugins
- [Amplitude Session](src/main/java/com/segment/analytics/destinations/plugins/AmplitudeSession.kt)
Sample plugin to enable users to enrich data for the Amplitude Actions destination

- [AppsFlyer](src/main/java/com/segment/analytics/destinations/plugins/AppsflyerDestination.kt)
Sample plugin to enable users to send data to the device-mode AppsFlyer destination

- [Comscore](src/main/java/com/segment/analytics/destinations/plugins/ComscoreDestination.kt)
Sample plugin to enable users to send data to the device-mode Comscore destination

- [Firebase](src/main/java/com/segment/analytics/destinations/plugins/FirebaseDestination.kt)
Sample plugin to enable users to send data to the device-mode Firebase destination 

- [Intercom](src/main/java/com/segment/analytics/destinations/plugins/IntercomDestination.kt)
Sample plugin to enable users to send data to the device-mode Intercom destination

- [Mixpanel](src/main/java/com/segment/analytics/destinations/plugins/MixpanelDestination.kt)
Sample plugin to enable users to send data to the device-mode Mixpanel destination

- [Webhook Plugin](src/main/java/com/segment/analytics/destinations/plugins/WebhookPlugin.kt)
An after plugin that allows you to send the event from the analytics timeline to a webhook of your choice. Ideal for debugging payloads in an internal network.
