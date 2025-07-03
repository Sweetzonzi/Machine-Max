import 'package:flutter/material.dart';

import 'bridge.dart';
import 'utils.dart';

enum HudStatus { on, off, blink }

class HudSmartWidget extends StatefulWidget {
  final String widgetName;
  final Widget child; // 需要闪烁的子组件
  final int milliseconds = 330; // 单次闪烁周期（默认 330ms）
  late final Duration blinkDuration; // 单次闪烁周期（默认 50ms）
  final double blinkRatio = 0.5; // 闪烁时间占比（0~1，默认 0.5 即亮灭各半）

  HudSmartWidget({super.key, required this.widgetName, required this.child}) {
    this.blinkDuration = Duration(milliseconds: milliseconds);
  }

  @override
  State<HudSmartWidget> createState() => _HudSmartWidgetState();
}

class _HudSmartWidgetState extends State<HudSmartWidget>
    with SingleTickerProviderStateMixin, BridgeWidgetStateMixin {
  @override
  late BridgeAttr attr = BridgeAttr(
    tag: "hud",
    category: "smart",
    widgetName: widget.widgetName,
  );
  late AnimationController _controller;
  late Animation<double> _opacityAnimation;
  HudStatus _status = HudStatus.on;
  double _switchValue = 1;

  @override
  void receive(List<dynamic> data) {
    setState(() => _status = HudStatus.values.byName(data[0]));
  }

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: widget.blinkDuration,
    );
    // 定义动画：从 1→0→1 快速切换（无渐变）
    _opacityAnimation = Tween<double>(
      begin: 2.0 * _switchValue,
      end: 0.012,
    ).animate(
      CurvedAnimation(
        parent: _controller,
        curve: Interval(
          0.0, // 从 0% 开始
          widget.blinkRatio, // 亮灭切换点（如 0.5 表示前半周期亮，后半周期灭）
        ),
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    while (Utils.isCalling) {}
    Utils.PAYLOADS.remove(hashCode);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    switch (_status) {
      case (HudStatus.on):
        _switchValue = 1;
        _controller.reset();
      case HudStatus.off:
        _switchValue = 0.07;
        _controller.reset();
      case HudStatus.blink:
        _switchValue = 1;
        _controller.repeat();
    }

    return AnimatedBuilder(
      animation: _opacityAnimation,
      builder: (context, child) {
        // 强制不透明或完全透明（无渐变）
        final opacity = _opacityAnimation.value.clamp(0.0, 1.0);
        return Opacity(opacity: opacity * _switchValue, child: widget.child);
      },
    );
  }
}
