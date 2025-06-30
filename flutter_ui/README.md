# flutter_ui


一个Machine-Max使用的，基于Flutter框架的前端ui组件

目的是做出美观、写实的mod ui

目前实现了自定义屏幕、Java与Dart双端互通的websocket信号系统、页面路由

后期打算：
* 方便的注册双端联动组件
* 从Java可注册自定义ui组件的功能
* 多设备分屏与OP管理系统
* 基于富文本编辑器的JS编辑器


## 快速上手

[Flutter安装教程](https://blog.csdn.net/ambergreen/article/details/141058085)、
[SDK下载](https://docs.flutter.cn/install/manual)、
[Flutter文档](https://docs.flutter.cn/)、
[Dart文档](https://dart.cn/docs/)、
[功能丰富的软件库](https://fluttergems.dev/)、
[各种教程视频](https://space.bilibili.com/3493136194079317/lists/2939302?type=season)

## 打包

首先你需要先运行下面的命令来打包为web应用
```cmd
cd .\flutter_ui\
flutter pub get
flutter build web
```
1. 运行完毕，你会在 flutter_ui/build 中发现多了一个web文件夹（或者被更新
2. 将web文件夹压缩成web.zip文件
3. 然后将web.zip文件复制（或替换）到 src/main/resources/webapp 中
4. 接下来就可以执行模组gradle的Jar打包了

## 命令帮助

获取帮助的命令
```cmd
flutter --help
```
如果你想获取某个特定命令段的帮助信息，在它后面写 --help ，比如：
```cmd
flutter build --help
```
```cmd
flutter build web --help
```


## 关于框架
Flutter是由Google开发的开源跨平台应用开发框架，使用Dart语言构建高性能的移动、Web和桌面应用程序。‌ 它以其快速开发能力、高性能渲染引擎和丰富的组件库著称，适用于iOS、Android、Web、Windows、macOS和Linux等多个平台。

Flutter的核心特性‌

* 跨平台开发‌：Flutter允许开发者使用单一代码库构建适用于多个平台的应用程序，包括移动端（iOS和Android）、Web以及桌面端（Windows、macOS和Linux）。‌

* 高性能渲染‌：Flutter使用自研的Skia渲染引擎，直接编译为原生机器代码，避免了传统跨平台框架中常见的性能瓶颈，能够实现流畅的60fps动画效果。‌

* 热重载功能‌：开发者可以实时查看代码修改后的效果，无需重新启动应用，显著提升开发效率。‌



## Flutter Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Lab: Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Cookbook: Useful Flutter samples](https://docs.flutter.dev/cookbook)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
