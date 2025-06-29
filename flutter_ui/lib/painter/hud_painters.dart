import 'package:flutter/material.dart';

import 'package:json_annotation/json_annotation.dart';
part 'hud_painters.g.dart'; // 1. 为需要序列化的类添加 @JsonSerializable() 注解
                            // 2. 在 package 目录下运行 dart run build_runner build 命令后生成该解析器
                            // 3. 在需要序列化的类中添加 fromJson toJson 方法链接到生成的解析器

// 生成器：根据 width 生成多层线条的 Paint 迭代器
Iterable<Paint> createLinePaints(double width) sync* {
  // 预定义线条配置（颜色 + 粗细系数）
  final paintConfigs = [
    {'color': const Color(0xff82ff5d), 'width': 2.7}, // 最粗层
    {'color': const Color(0xff43ff00), 'width': 2.2}, // 次粗层
    {'color': const Color(0x1262804F), 'width': 1.1}, // 中间层
    {'color': const Color(0x8ffffff), 'width': 0.9}, // 最细层
  ];
  // 按配置生成 Paint 对象（自动适配 width）
  for (final config in paintConfigs) {
    yield Paint()
      ..color = config['color'] as Color
      ..strokeWidth =
          (config['width'] as double) *
          width // 动态计算粗细
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round; // 统一端点样式
  }
}

@JsonSerializable()
class SimpleLinePainter extends CustomPainter {
  final double width;
  late final Iterator<Paint> _paintIterator;

  // 构造时初始化生成器迭代器
  SimpleLinePainter(this.width)
    : _paintIterator = createLinePaints(width).iterator;

  factory SimpleLinePainter.fromJson(Map<String, dynamic> json) =>
      _$SimpleLinePainterFromJson(json);

  Map<String, dynamic> toJson() => _$SimpleLinePainterToJson(this);

  @override
  void paint(Canvas canvas, Size size) {
    // 计算需要绘制的起点和终点（十字交叉线）
    final topLeft = size.topLeft(Offset.zero);
    final bottomRight = size.bottomRight(Offset.zero);
    final topRight = size.topRight(Offset.zero);
    final bottomLeft = size.bottomLeft(Offset.zero);

    while (_paintIterator.moveNext()) {
      // 绘制第一条对角线（topLeft → bottomRight）
      // 依次使用生成器的 4 种 Paint 配置
      canvas.drawLine(topLeft, bottomRight, _paintIterator.current);
      canvas.drawLine(topLeft, bottomRight, _paintIterator.current);
      canvas.drawLine(topLeft, bottomRight, _paintIterator.current);
      canvas.drawLine(topLeft, bottomRight, _paintIterator.current);
      // 绘制第二条对角线（topRight → bottomLeft）
      // 复用生成器的 4 种 Paint 配置（顺序与第一条线一致）
      canvas.drawLine(topRight, bottomLeft, _paintIterator.current);
      canvas.drawLine(topRight, bottomLeft, _paintIterator.current);
      canvas.drawLine(topRight, bottomLeft, _paintIterator.current);
      canvas.drawLine(topRight, bottomLeft, _paintIterator.current);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

@JsonSerializable()
class HudRoundPainter extends CustomPainter {
  final double radius;
  final double width;
  late final Iterator<Paint> _paintIterator;

  // 构造时初始化生成器迭代器
  HudRoundPainter(this.radius, this.width)
    : _paintIterator = createLinePaints(width).iterator;

  factory HudRoundPainter.fromJson(Map<String, dynamic> json) =>
      _$HudRoundPainterFromJson(json);

  Map<String, dynamic> toJson() => _$HudRoundPainterToJson(this);
  
  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    while (_paintIterator.moveNext()) {
      // 绘制多层圆（依次使用生成器的 4 种 Paint 配置）
      canvas.drawCircle(center, radius, _paintIterator.current);
      canvas.drawCircle(center, radius, _paintIterator.current);
      canvas.drawCircle(center, radius, _paintIterator.current);
      canvas.drawCircle(center, radius, _paintIterator.current);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
