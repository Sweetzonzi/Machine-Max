import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/svg.dart';

import '../Utils.dart';
import '../blink.dart';
import '../painter/hud_painters.dart';

class HudScreen extends StatefulWidget {
  const HudScreen({super.key});

  @override
  State<HudScreen> createState() => _HudScreenState();
}

class _HudScreenState extends State<HudScreen> {
  final audioContext =
      AudioContextConfig(focus: AudioContextConfigFocus.mixWithOthers).build();
  late AudioPlayer player;
  var alpha = 1.0;

  @override
  Widget build(BuildContext context) {
    var bgSize = MediaQuery.sizeOf(context);
    var hudLength = min(bgSize.width, bgSize.height);
    var widthSide = (bgSize.width - bgSize.height) / 2;
    var heightSide = (bgSize.height - bgSize.width) / 2;
    var pd =
        bgSize.width > bgSize.height
            ? EdgeInsets.only(left: widthSide, right: widthSide)
            : EdgeInsets.only(top: heightSide, bottom: heightSide);

    TextStyle laserFontStyle() {
      return TextStyle(
        fontSize: hudLength / 13,
        color: Color(0xff66FA31), // 线条颜色,
        // foreground:
        //     Paint()
        //       ..color = Color(0xff43ff00) // 线条颜色
        //       ..strokeWidth =
        //           2.1 // 线条粗细
        //       ..style = PaintingStyle.stroke
        //       ..strokeCap = StrokeCap.round,
      );
    }

    Widget HudSmartText(String tag, String text, int milliseconds) {
      return HudSmartWidget(
        widgetName: tag,
        child: Text(text, style: laserFontStyle()),
      );
    }

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Listener(
        onPointerSignal: (event) {
          if (event is PointerScrollEvent) {
            var dy = event.scrollDelta.dy;
            if (dy > 0) {
              alpha *= 0.92;
              if (alpha < 0.06) alpha = 0.06;
            }
            if (dy < 0) {
              alpha *= 1 + (1 - 0.92);
              if (alpha > 1) alpha = 1;
            }
            setState(() {});
          }
        },

        child: Padding(
          padding: pd,
          child: Container(
            width: bgSize.width,
            height: bgSize.height,
            color: kDebugMode ? Colors.black87 : Color(0x3F7C1EBE),
            child: Opacity(
              opacity: alpha,
              child: Stack(
                children: [
                  Positioned(
                    width: hudLength / 8,
                    height: hudLength / 8,
                    child: HudSmartWidget(
                      widgetName: "block_cursor",
                      child: SvgPicture.asset("assets/svg/block_cursor.svg"),
                    ),
                  ),
                  Positioned.fill(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        HudSmartText("pull_up", "PULL UP", 330),
                        HudSmartText("shoot", "SHOOT", 330),
                      ],
                    ),
                  ),

                  Positioned.fill(
                    child: HudSmartWidget(
                      widgetName: "x_line",
                      child: CustomPaint(
                        painter: SimpleLinePainter(hudLength / 900),
                      ),
                    ),
                  ),
                  Positioned.fill(
                    child: HudSmartWidget(
                      widgetName: "round",
                      child: CustomPaint(
                        painter: HudRoundPainter(
                          hudLength / 3,
                          hudLength / 260,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    Utils.connect();
  }

  @override
  void dispose() {
    player.dispose();
    super.dispose();
  }

  Future<void> playBGM() async {
    player = AudioPlayer();
    AudioPlayer.global.setAudioContext(audioContext).then((_) async {
      await player.setAudioContext(audioContext);
      await player.setPlayerMode(PlayerMode.mediaPlayer);
      await player.setReleaseMode(ReleaseMode.release);
      await player.setSource(AssetSource("audio/alert.mp3"));
      await player.resume().catchError((onError) {
        print('播放失败: $onError');
      });
    });
  }
}
