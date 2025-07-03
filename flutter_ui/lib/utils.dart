import 'dart:convert';
import 'dart:typed_data';

import 'package:web_socket_channel/web_socket_channel.dart';

class Utils {
  static bool isCalling = false;
  static bool connecting = false;
  static Map<int, void Function(Map)> PAYLOADS = {};
  static WebSocketChannel? channel;
  static Future<void> connect() async {
    if (connecting) return;
    connecting = true;
    final wsUrl = Uri.parse('ws://localhost:8194/');
    channel = WebSocketChannel.connect(wsUrl)
      ..stream.listen(
        (data) {
          isCalling = true;
          for (var event in PAYLOADS.values) {
            if (data is Uint8List) {
              dynamic rawJson = json.decode(Utf8Decoder().convert(data));
              Map<String, dynamic> payload = Map<String, dynamic>.from(rawJson);
              event.call(payload);
            }

          }
          isCalling = false;
        },
        onError: (error) {
          Future.delayed(Duration(seconds: 5), () {
            connecting = false;
            connect(); //尝试重连
          });
        },
        onDone: () { //服务器关闭
          Future.delayed(Duration(seconds: 5), () {
            connecting = false;
            connect(); //尝试重连
          });
        },
      );
  }
}
