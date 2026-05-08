// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:husk_plakaten_flutter/main.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  testWidgets('Husk-plakaten renders base UI', (WidgetTester tester) async {
    await tester.pumpWidget(const HuskPlakatenApp());
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Husk-plakaten'), findsOneWidget);
    expect(find.byIcon(Icons.add), findsOneWidget);
  });
}
