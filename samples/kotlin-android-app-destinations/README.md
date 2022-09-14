# Sample App
This is a sample android app that uses the `analytics-kotlin` library and the new `Plugins` concepts. It is meant to be simplistic, and easy to understand all the while showcasing the power of the analytics-kotlin library

## Plugins
- [Amplitude Session](https://github.com/segment-integrations/analytics-kotlin-amplitude)
Plugin to enable users to enrich data for the Amplitude Actions destination

- [Appcues](https://github.com/appcues/segment-appcues-android)
Plugin to enable users to send data to the device-mode Appcues destination

- [AppsFlyer](https://github.com/segment-integrations/analytics-kotlin-appsflyer)
Plugin to enable users to send data to the device-mode AppsFlyer destination

- [Comscore](https://github.com/segment-integrations/analytics-kotlin-comscore)
Plugin to enable users to send data to the device-mode Comscore destination

- [Firebase](https://github.com/segment-integrations/analytics-kotlin-firebase)
Plugin to enable users to send data to the device-mode Firebase destination 

- [Intercom](https://github.com/segment-integrations/analytics-kotlin-intercom)
Plugin to enable users to send data to the device-mode Intercom destination

- [Mixpanel](https://github.com/segment-integrations/analytics-kotlin-mixpanel)
Plugin to enable users to send data to the device-mode Mixpanel destination

- [Webhook Plugin](src/main/java/com/segment/analytics/kotlin/destinations/plugins/WebhookPlugin.kt)
An after plugin that allows you to send the event from the analytics timeline to a webhook of your choice. Ideal for debugging payloads in an internal network.
