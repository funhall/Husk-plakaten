import 'dart:io';

/// Læser [version.properties] i repo-roden og sætter `version:` i flutter_app/pubspec.yaml.
/// Kør fra repo-rod: dart scripts/sync_app_version.dart
void main() {
  final repoRoot = File(Platform.script.toFilePath()).parent.parent.path;
  final propsFile = File('$repoRoot/version.properties');
  if (!propsFile.existsSync()) {
    stderr.writeln('Mangler ${propsFile.path}');
    exitCode = 1;
    return;
  }

  final props = <String, String>{};
  for (final line in propsFile.readAsLinesSync()) {
    final t = line.trim();
    if (t.isEmpty || t.startsWith('#')) continue;
    final i = t.indexOf('=');
    if (i <= 0) continue;
    props[t.substring(0, i).trim()] = t.substring(i + 1).trim();
  }

  final name = props['VERSION_NAME'];
  final code = props['VERSION_CODE'];
  if (name == null || code == null) {
    stderr.writeln('VERSION_NAME og VERSION_CODE skal være sat i version.properties');
    exitCode = 1;
    return;
  }

  final pubspecFile = File('$repoRoot/flutter_app/pubspec.yaml');
  if (!pubspecFile.existsSync()) {
    stderr.writeln('Mangler ${pubspecFile.path}');
    exitCode = 1;
    return;
  }

  final raw = pubspecFile.readAsStringSync();
  final lines = raw.split(RegExp(r'\r?\n'));
  var replaced = false;
  final out = <String>[];
  for (final line in lines) {
    if (!replaced && line.trimLeft().startsWith('version:')) {
      out.add('version: $name+$code');
      replaced = true;
    } else {
      out.add(line);
    }
  }
  if (!replaced) {
    stderr.writeln('Kunne ikke finde version:-linje i pubspec.yaml');
    exitCode = 1;
    return;
  }
  pubspecFile.writeAsStringSync(out.join('\n'));
}
