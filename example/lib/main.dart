import 'dart:async';

import 'package:flutter/material.dart';
import 'package:android_overlay/android_overlay.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool isActive = false;
  bool permissionStatus = false;
  String overlayPosition = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      getOverlayStatus();
      getPermissionStatus();
    });
  }

  Future<void> getOverlayStatus() async {
    isActive = await AndroidOverlay.isActive();
    setState(() {});
  }

  Future<void> getPermissionStatus() async {
    permissionStatus = await AndroidOverlay.checkPermission();
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
            title: const Text(
              'Flutter overlay pop up',
              style: TextStyle(color: Colors.white),
            ),
            backgroundColor: Colors.red[900]),
        body: SizedBox(
          width: double.maxFinite,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisAlignment: MainAxisAlignment.start,
            children: [
              const SizedBox(height: 30),
              Text(
                'Permission status: ${permissionStatus ? 'enabled' : 'disabled'}',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              MaterialButton(
                onPressed: () async {
                  permissionStatus = await AndroidOverlay.requestPermission();
                  setState(() {});
                },
                color: Colors.red[900],
                child: const Text(
                  'Request overlay permission',
                  style: TextStyle(color: Colors.white),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                'Is active: $isActive',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              MaterialButton(
                onPressed: () async {
                  final permission = await AndroidOverlay.checkPermission();
                  if (permission) {
                    if (!await AndroidOverlay.isActive()) {
                      isActive = await AndroidOverlay.showOverlay(
                        width: 120,
                        height: 120,
                        alignment: OverlayAlignment.center,
                        draggable: true,
                        entryPoint: customOverlay,
                      );
                      setState(() {
                        isActive = isActive;
                      });
                      return;
                    } else {
                      final result = await AndroidOverlay.closeOverlay();
                      setState(() {
                        isActive = (result == true) ? false : true;
                      });
                    }
                  } else {
                    permissionStatus = await AndroidOverlay.requestPermission();
                    setState(() {});
                  }
                },
                color: Colors.red[900],
                child: const Text(
                  'Show overlay',
                  style: TextStyle(color: Colors.white),
                ),
              ),
              MaterialButton(
                onPressed: () async {
                  if (await AndroidOverlay.isActive()) {
                    await AndroidOverlay.sendToOverlay(
                        {'mssg': 'Hello from dart!'});
                  }
                },
                color: Colors.red[900],
                child: const Text('Send data',
                    style: TextStyle(color: Colors.white)),
              ),
              MaterialButton(
                onPressed: () async {
                  if (await AndroidOverlay.isActive()) {
                    await AndroidOverlay.updateOverlay(
                      x: 0,
                      y: 0,
                      snapping: true
                    );
                  }
                },
                color: Colors.red[900],
                child: const Text('Update overlay',
                    style: TextStyle(color: Colors.white)),
              ),
              MaterialButton(
                onPressed: () async {
                  if (await AndroidOverlay.isActive()) {
                    final position = await AndroidOverlay.getOverlayPosition();
                    setState(() {
                      overlayPosition = (position?['overlayPosition'] != null)
                          ? position!['overlayPosition'].toString()
                          : '';
                    });
                  }
                },
                color: Colors.red[900],
                child: const Text('Get overlay position',
                    style: TextStyle(color: Colors.white)),
              ),
              Text('Current position: $overlayPosition'),
            ],
          ),
        ),
      ),
    );
  }
}

///
/// Is required has `@pragma("vm:entry-point")` and the method name by default is `androidOverlay`
/// if you change the method name you should pass it as `entryPoint` in showOverlay method
///
@pragma("vm:entry-point")
void customOverlay() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(MaterialApp(
    debugShowCheckedModeBanner: false,
    home: FloatingActionButton(
      shape: const CircleBorder(),
      backgroundColor: Colors.red,
      elevation: 0,
      onPressed: () async {
        await AndroidOverlay.closeOverlay();
        await AndroidOverlay.backToApp();
      },
      child: const Text('X', style: TextStyle(color: Colors.white, fontSize: 20)),
    )
  ));
}