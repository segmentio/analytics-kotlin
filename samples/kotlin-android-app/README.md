# Sample App
This is a sample android app that uses the `analytics-kotlin` library and the new `Plugins` concepts. It is meant to be simplistic, and easy to understand all the while showcasing the power of the analytics-kotlin library

## Plugins
- [Android AdvertisingId Plugin](src/main/java/com/segment/analytics/next/plugins/AndroidAdvertisingIdPlugin.kt)
Using the `play-services-ads` library this plugin adds the `advertisingId` to all payloads (under the `context` key) going through the analytics timeline

- [Android Record Screen Plugin](src/main/java/com/segment/analytics/next/plugins/AndroidRecordScreenPlugin.kt)
Using the application lifecycle, this plugin automatically sends `Screen` events through the analytics timeline, on Activity start

- [Consent Tracking](src/main/java/com/segment/analytics/next/plugins/ConsentTracking.kt)
Presents user with a dialog to consent to tracking. If consent is given, any queued events will be sent out to the analytics timeline. If consent is not given, all queued events and future events will be dropped
** Note: You will have to switch to the `ConsentActivity` inside of AndroidManifest.xml to view this feature **

- [Push Notification Tracking](src/main/java/com/segment/analytics/next/plugins/PushNotificationTracking.kt)
Using Firebase Service for push notifications this plugin hooks into the activity start lifecycle method and fires Push Notification track events.

## Tracking Deep Links
The sample app is configured to open links with the schema and hostname `https://segment-sample.com`

Here is how you can do it via adb
```bash
adb shell am start -W -a android.intent.action.VIEW -d "https://segment-sample.com?utm_source=cli\&utm_click=2" com.segment.analytics.next
```

## Firebase Cloud Messaging
This project is setup to track push notification received and opened events. This code is strictly optional and must be customized as per your needs. The code here is only for demonstration purposes
### Setup
- Add your FCM project's `google-services.json` to this folder
- Modify `MyFirebaseService.kt` to customize the notification displayed to the user.
- Here is how to send a push notification using cURL [this uses the legacy api](https://firebase.google.com/docs/cloud-messaging/send-message#send-messages-using-the-legacy-app-server-protocols)
```bash
curl --request POST \
  --url https://fcm.googleapis.com/fcm/send \
  --header 'Authorization: key=<SERVER_KEY>' \
  --header 'Content-Type: application/json' \
  --data '{
	"data": {
		"title": "Hello World"
		"content": "You have mail",
	},
	"to": "<FCM_TOKEN_FOR_DEVICE>"
}'
```

### How it works
- We have 2 core changes
  - MyFirebaseService.kt
  The core component to handle FCM push messages. This is responsible for handling the incoming message and assigning the intents for the notification. It is also responsible for firing the `Push Notification Received` event.
  - PushNotificationTracking.kt
  The analytics plugin responsible for firing the "Push Notification Tapped" event. This is a lifecycle plugin that will be invoked for any Activity onCreate.