import 'dart:math';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:husk_plakaten_flutter/data/plakat_database.dart';
import 'package:husk_plakaten_flutter/data/plakat_repository.dart';
import 'package:husk_plakaten_flutter/models/plakat.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (!kIsWeb && (Platform.isWindows || Platform.isLinux)) {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  }
  runApp(const HuskPlakatenApp());
}

class HuskPlakatenApp extends StatelessWidget {
  const HuskPlakatenApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Husk-plakaten',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
      ),
      home: const PlakatHomePage(),
    );
  }
}

class PlakatHomePage extends StatefulWidget {
  const PlakatHomePage({super.key});

  @override
  State<PlakatHomePage> createState() => _PlakatHomePageState();
}

class _PlakatHomePageState extends State<PlakatHomePage>
    with SingleTickerProviderStateMixin {
  static const String _demoUserId = 'demo@huskplakaten.dk';
  late final PlakatRepository _repository;
  late final TabController _tabController;
  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _latController = TextEditingController();
  final TextEditingController _lngController = TextEditingController();
  final Random _random = Random();

  bool _loading = true;
  List<Plakat> _active = const <Plakat>[];
  List<Plakat> _removed = const <Plakat>[];
  int _count = 0;

  @override
  void initState() {
    super.initState();
    _repository = PlakatRepository(PlakatDatabase.instance);
    _tabController = TabController(length: 2, vsync: this);
    _reload();
  }

  Future<void> _reload() async {
    setState(() {
      _loading = true;
    });
    final active = await _repository.getActive(_demoUserId);
    final removed = await _repository.getRemoved(_demoUserId);
    final count = await _repository.countAll(_demoUserId);
    if (!mounted) {
      return;
    }
    setState(() {
      _active = active;
      _removed = removed;
      _count = count;
      _loading = false;
    });
  }

  Future<void> _showCreateDialog() async {
    _titleController.clear();
    _latController.text = '55.6761';
    _lngController.text = '12.5683';

    await showDialog<void>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Ny plakat'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              TextField(
                controller: _titleController,
                decoration: const InputDecoration(
                  labelText: 'Titel (gemmes som QR-tekst)',
                ),
              ),
              TextField(
                controller: _latController,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(labelText: 'Latitude'),
              ),
              TextField(
                controller: _lngController,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(labelText: 'Longitude'),
              ),
            ],
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Annuller'),
            ),
            FilledButton(
              onPressed: () async {
                final lat = double.tryParse(_latController.text.trim());
                final lng = double.tryParse(_lngController.text.trim());
                final title = _titleController.text.trim();
                if (lat == null || lng == null || title.isEmpty) {
                  return;
                }

                await _repository.insert(
                  Plakat(
                    ownerUserId: _demoUserId,
                    createdAtMillis: DateTime.now().millisecondsSinceEpoch,
                    latitude: lat,
                    longitude: lng,
                    qrText: title,
                  ),
                );
                if (!context.mounted) {
                  return;
                }
                Navigator.of(context).pop();
                await _reload();
              },
              child: const Text('Gem'),
            ),
          ],
        );
      },
    );
  }

  Future<void> _delete(Plakat item) async {
    await _repository.delete(item);
    await _reload();
  }

  Future<void> _markRemoved(Plakat item) async {
    final id = item.id;
    if (id == null) {
      return;
    }
    final lat = item.latitude + (_random.nextDouble() - 0.5) * 0.0002;
    final lng = item.longitude + (_random.nextDouble() - 0.5) * 0.0002;
    await _repository.markAsRemoved(
      id: id,
      removedAtMillis: DateTime.now().millisecondsSinceEpoch,
      removedLatitude: lat,
      removedLongitude: lng,
    );
    await _reload();
  }

  @override
  void dispose() {
    _titleController.dispose();
    _latController.dispose();
    _lngController.dispose();
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Husk-plakaten'),
        bottom: TabBar(
          controller: _tabController,
          tabs: <Widget>[
            Tab(text: 'Aktive (${_active.length})'),
            Tab(text: 'Fjernede (${_removed.length})'),
          ],
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: <Widget>[
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: Text('Registrerede plakater: $_count'),
                  ),
                ),
                Expanded(
                  child: TabBarView(
                    controller: _tabController,
                    children: <Widget>[
                      _PosterList(
                        items: _active,
                        emptyText: 'Ingen aktive plakater endnu.',
                        showMarkRemoved: true,
                        onDelete: _delete,
                        onMarkRemoved: _markRemoved,
                      ),
                      _PosterList(
                        items: _removed,
                        emptyText: 'Ingen fjernede plakater endnu.',
                        showMarkRemoved: false,
                        onDelete: _delete,
                        onMarkRemoved: _markRemoved,
                      ),
                    ],
                  ),
                ),
              ],
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showCreateDialog,
        child: const Icon(Icons.add),
      ),
    );
  }
}

class _PosterList extends StatelessWidget {
  const _PosterList({
    required this.items,
    required this.emptyText,
    required this.showMarkRemoved,
    required this.onDelete,
    required this.onMarkRemoved,
  });

  final List<Plakat> items;
  final String emptyText;
  final bool showMarkRemoved;
  final Future<void> Function(Plakat item) onDelete;
  final Future<void> Function(Plakat item) onMarkRemoved;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return Center(child: Text(emptyText));
    }
    return ListView.builder(
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        final created = DateTime.fromMillisecondsSinceEpoch(item.createdAtMillis);
        final removedText = item.removedAtMillis == null
            ? ''
            : ' | Fjernet: ${DateTime.fromMillisecondsSinceEpoch(item.removedAtMillis!)}';
        return Card(
          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: ListTile(
            title: Text(item.qrText ?? 'Plakat ${item.id ?? ''}'),
            subtitle: Text(
              'GPS: ${item.latitude.toStringAsFixed(5)}, ${item.longitude.toStringAsFixed(5)}'
              '\nOprettet: $created$removedText',
            ),
            isThreeLine: true,
            trailing: Wrap(
              spacing: 8,
              children: <Widget>[
                if (showMarkRemoved)
                  IconButton(
                    tooltip: 'Markér fjernet',
                    icon: const Icon(Icons.check_circle_outline),
                    onPressed: () => onMarkRemoved(item),
                  ),
                IconButton(
                  tooltip: 'Slet',
                  icon: const Icon(Icons.delete_outline),
                  onPressed: () => onDelete(item),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}