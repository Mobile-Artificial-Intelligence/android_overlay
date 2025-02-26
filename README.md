# android_overlay

A Flutter plugin for displaying an overlay on top of the Android system UI.

## Getting Started

Add this to your package's pubspec.yaml file:

```yaml
dependencies:
  android_overlay: ^0.0.2
```

Now in your Dart code, you can use:

```dart
import 'package:android_overlay/android_overlay.dart';
```

## Android

add this to your AndroidManifest.xml

```dart
 <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

 <application>
        ...
        <service
           android:name="com.danemadsen.android_overlay.AndroidOverlayService"
           android:exported="false" />
    </application>
```

### Android 14

applications that target SDK 34 and use foreground service should include foregroundServiceType attribute([see documentation](https://developer.android.com/about/versions/14/changes/fgs-types-required)).

```dart
 <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

 <application>
        ...
        <service
           android:name="com.danemadsen.android_overlay.AndroidOverlayService"
           android:exported="false"
           <!-- add this -->
           android:foregroundServiceType="camera, dataSync, location, etc" />
    </application>
```

## Flutter implementation

configure your main.dart entry point a widget to display (make sure to add @pragma('vm:entry-point'))

**NOTE:**
Now you can pass as parameter the dart entry point method name when showOverlay is called

```dart
@pragma("vm:entry-point")
void androidOverlay() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MaterialApp(
    debugShowCheckedModeBanner: false,
    home: Text('Hello Pub.dev!'),
  ));
}
```

## Overlay Methods

  returns true when overlay permission is alreary granted if permission is not granted then open app settings

  ```dart
  await AndroidOverlay.requestPermission();
  ```

  returns true or false according to permission status

  ```dart
  await AndroidOverlay.checkPermission();
  ```

  display your overlay and return true if is showed

*PARAMS*

- `x` position of the overlay
- `y` position of the overlay
- `height` is not required by default is MATCH_PARENT
- `width` is not required by default is MATCH_PARENT
- `alignment` is not required by default is CENTER for more info see: <https://developer.android.com/reference/android/view/Gravity>
- `snapping` by default is false therefore the overlay can´t be snapped to the edges of the screen.
- `draggable`  by default is false therefore the overlay can´t be dragged.
- `entryPointMethodName` by default is 'androidOverlay' if you want you can change it

  ```dart
  await AndroidOverlay.showOverlay();
  ```

  returns true if overlay closed correctly or already is closed

  ```dart
  await AndroidOverlay.closeOverlay();
  ```

  returns the overlay status true = open, false = closed

  ```dart
  await AndroidOverlay.isActive();
  ```

  returns the last overlay position if drag is enabled

  ```dart
  await AndroidOverlay.getOverlayPosition();
  ```

  share dynamic data to overlay

  ```dart
  await AndroidOverlay.sendToOverlay({'data':'hello!'});
  await AndroidOverlay.sendToOverlay('hello');
  ```

  receive the data from flutter as stream

  ```dart
  await AndroidOverlay.dataListener();
  ```
