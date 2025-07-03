import 'package:flutter/cupertino.dart';

import 'Utils.dart';

interface class BridgeWidget {
  void receive(List<dynamic> data) {}
  late BridgeAttr attr;
}

abstract mixin class BridgeWidgetStateMixin<T extends StatefulWidget> implements BridgeWidget{
  void initState() {
    Utils.PAYLOADS[hashCode] = (payload) {
      List<dynamic>? args = payload[attr.tag];
      if (args != null && args[0] == attr.category && args[1] == attr.widgetName) {
        receive(args..removeAt(0)..removeAt(0));
      }
    };
  }
}

class BridgeAttr {
  final String tag;
  final String category;
  final String widgetName;
  BridgeAttr({required this.tag, required this.category, required this.widgetName});
}