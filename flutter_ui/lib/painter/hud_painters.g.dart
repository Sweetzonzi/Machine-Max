// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'hud_painters.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SimpleLinePainter _$SimpleLinePainterFromJson(Map<String, dynamic> json) =>
    SimpleLinePainter((json['width'] as num).toDouble());

Map<String, dynamic> _$SimpleLinePainterToJson(SimpleLinePainter instance) =>
    <String, dynamic>{'width': instance.width};

HudRoundPainter _$HudRoundPainterFromJson(Map<String, dynamic> json) =>
    HudRoundPainter(
      (json['radius'] as num).toDouble(),
      (json['width'] as num).toDouble(),
    );

Map<String, dynamic> _$HudRoundPainterToJson(HudRoundPainter instance) =>
    <String, dynamic>{'radius': instance.radius, 'width': instance.width};
