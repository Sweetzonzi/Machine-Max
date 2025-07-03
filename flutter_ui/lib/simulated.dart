import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

interface class BgWidget {
  Widget backgroundBuild(BuildContext context) {
    // TODO: implement backgroundBuild
    throw UnimplementedError();
  }
}

abstract mixin class FakeMinecraftBackGround<T extends StatefulWidget> implements BgWidget{
  Widget build(BuildContext context) {
    return Stack(children: [
      kDebugMode ? Positioned.fill(child: Image.asset("img/bg01.png", fit: BoxFit.cover,),) : const SizedBox(),
      Transform.scale(scale: kDebugMode ? 0.8 : 1, child: backgroundBuild(context),)
    ],);
  }
}