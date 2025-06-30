import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'dart:math';

import 'Utils.dart';
import 'screen/hud_screen.dart';


void main() {
  runApp(const MyApp());
}

/// The route configuration.
final GoRouter _router = GoRouter(
  routes: <RouteBase>[
    GoRoute(
      path: '/hud',
      builder: (BuildContext context, GoRouterState state) {
        return const HudScreen();
      },
    ),

  ],
);


class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    var m = MediaQuery.of(context);
    var mined = min(m.size.width, m.size.height);
    ThemeData darkTheme = ThemeData(
      brightness: Brightness.dark,
      primarySwatch: Colors.blue, // 主色（暗黑模式下可能自动调整亮度）
      scaffoldBackgroundColor: Color(0xFF121212), // 深灰色背景
      appBarTheme: AppBarTheme(
        titleTextStyle: TextStyle(color: Colors.white, fontSize: mined / 30),
        toolbarHeight: mined / 10,
      ),
      textTheme: TextTheme(
        bodyLarge: TextStyle(color: Colors.white),
        bodyMedium: TextStyle(color: Colors.white),
        bodySmall: TextStyle(color: Colors.white),
      ),
      iconTheme: IconThemeData(color: Colors.white70, size: 25), // 图标颜色
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        iconSize: 70,
        sizeConstraints: BoxConstraints(
          minWidth: mined / 10,
          minHeight: mined / 10,
        ),
      ),
      // 其他自定义样式...
    );
    return MaterialApp.router(
      title: 'Machine-Max Flutter Project',
      theme: darkTheme,
      routerConfig: _router,
    );
  }
}
