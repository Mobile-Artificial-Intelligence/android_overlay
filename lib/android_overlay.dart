import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const kMatchParent = -1;

class AndroidOverlay {
  AndroidOverlay._();

  static final StreamController _mssgController = StreamController.broadcast();

  static const _methodChannel = MethodChannel('android_overlay');
  static const _messageChannel =
      BasicMessageChannel('android_overlay_msg', JSONMessageCodec());

  ///
  /// returns true when overlay permission is alreary granted
  /// if permission is not granted then open app settings
  ///
  static Future<bool> requestPermission() async {
    final result =
        await _methodChannel.invokeMethod<bool?>('requestPermission');
    return result ?? false;
  }

  ///
  /// returns true or false according to permission status
  ///
  static Future<bool> checkPermission() async {
    final result = await _methodChannel.invokeMethod<bool?>('checkPermission');
    return result ?? false;
  }

  ///
  /// display your overlay and return true if is showed
  /// [height] is not required by default is MATCH_PARENT
  /// [width] is not required by default is MATCH_PARENT
  /// [alignment] is not required by default is CENTER for more info see: https://developer.android.com/reference/android/view/WindowManager.LayoutParams
  /// [backgroundBehavior] by default is focusable flag that is you can take focus inside a overlay for example inside a textfield and [tapThrough] you can tap through the overlay background even if has MATCH_PARENT sizes.
  /// [screenOrientation] by default is portrait its param define the overlay orientation.
  ///
  static Future<bool> showOverlay({
    double? x,
    double? y,
    int? height,
    int? width,
    OverlayAlignment? alignment,
    bool? draggable = false,
    bool? snapping = false,
    VoidCallback? entryPoint,
  }) async {
    final result = await _methodChannel.invokeMethod<bool?>('showOverlay', {
      /// the x position of the overlay
      'x': x,

      /// the y position of the overlay
      'y': y,

      /// is not required by default is MATCH_PARENT
      'height': height,

      /// is not required by default is MATCH_PARENT
      'width': width,

      /// is not required by default is CENTER for more info see: https://developer.android.com/reference/android/view/Gravity
      'alignment': alignment?.value,

      /// by default is false therefore the overlay can´t be dragged.
      'draggable': draggable,

      /// by default is false therefore the overlay will not snap to the edges.
      'snapping': snapping,

      /// by default `androidOverlay`.
      'entryPoint': _extractFunctionName(entryPoint.toString()),
    });
    return result ?? false;
  }

  ///
  /// returns true if overlay closed correctly or already is closed
  ///
  static Future<bool?> closeOverlay() async {
    final result = await _methodChannel.invokeMethod<bool?>('closeOverlay');
    return result;
  }

  ///
  /// returns true if successfully back to app
  /// 
  static Future<bool?> backToApp() async {
    final result = await _methodChannel.invokeMethod<bool?>('backToApp');
    return result;
  }

  ///
  /// returns the overlay status true = open, false = closed
  ///
  static Future<bool> isActive() async {
    final result = await _methodChannel.invokeMethod<bool?>('isActive');
    return result ?? false;
  }

  ///
  /// returns the current overlay position if enable drag is enabled
  ///
  static Future<Map?> getOverlayPosition() async {
    final result =
        await _methodChannel.invokeMethod<Map?>('getOverlayPosition');
    return result;
  }

  ///
  /// update overlay layout
  ///
  static Future<bool> updateOverlay({
    double? x,
    double? y,
    int? height,
    int? width,
    bool? draggable,
    bool? snapping,
  }) async {
    final result = await _methodChannel.invokeMethod<bool?>('updateOverlay', {
      /// the new value for layout x position
      'x': x,

      /// the new value for layout y position
      'y': y,

      /// the new value for layout height
      'height': height,

      /// the new value for layout width
      'width': width,

      /// by default is false therefore the overlay can´t be dragged.
      'draggable': draggable,

      /// by default is false therefore the overlay will not snap to the edges.
      'snapping': snapping,
    });
    return result ?? false;
  }

  ///
  /// share dynamic data to overlay
  ///
  static Future<void> sendToOverlay(dynamic data) async {
    await _messageChannel.send(data);
  }

  ///
  /// receive the data from flutter
  ///
  static Stream<dynamic>? get dataListener {
    _messageChannel.setMessageHandler((mssg) async {
      if (_mssgController.isClosed) return '';
      _mssgController.add(mssg);
      return mssg;
    });

    if (_mssgController.isClosed) return null;
    return _mssgController.stream;
  }

  ///
  /// drain and close data stream controller
  ///
  static void stopDataLIstener() {
    try {
      _mssgController.stream.drain();
      _mssgController.close();
    } catch (e) {
      debugPrint(
          '[AndroidOverlay] Something went wrong when close overlay pop up: $e');
    }
  }

  static String _extractFunctionName(String callbackString) {
    RegExp regex = RegExp(r"Function '([^']+)'");
    Match? match = regex.firstMatch(callbackString);
    return match != null ? match.group(1)! : 'Unknown';
  }
}

enum OverlayAlignment {
  center(17),
  left(3),
  right(5);

  final int value;
  const OverlayAlignment(this.value);
}
