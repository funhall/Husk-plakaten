import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
import 'package:husk_plakaten_flutter/auth/auth_session_store.dart';
import 'package:husk_plakaten_flutter/data/plakat_database.dart';
import 'package:husk_plakaten_flutter/data/plakat_repository.dart';
import 'package:husk_plakaten_flutter/models/plakat.dart';
import 'package:image_picker/image_picker.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:url_launcher/url_launcher.dart';

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
      title: 'Husk mig',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
      ),
      home: const AppRootPage(),
    );
  }
}

class AppRootPage extends StatefulWidget {
  const AppRootPage({super.key});

  @override
  State<AppRootPage> createState() => _AppRootPageState();
}

class _AppRootPageState extends State<AppRootPage>
    with SingleTickerProviderStateMixin {
  static const double removeAllowedDistanceMeters = 10;
  static const double removeAlertResetDistanceMeters = 70;

  late final PlakatRepository _repository;
  late final AuthSessionStore _sessionStore;
  late final TabController _tabController;
  final ImagePicker _imagePicker = ImagePicker();
  final Map<int, bool> _nearbyAlertState = <int, bool>{};

  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  AuthSession? _session;
  Position? _currentLocation;
  List<Plakat> _active = const <Plakat>[];
  List<Plakat> _removed = const <Plakat>[];
  int _registeredCount = 0;
  bool _loading = true;
  String? _loginError;

  @override
  void initState() {
    super.initState();
    _sessionStore = AuthSessionStore();
    _repository = PlakatRepository(PlakatDatabase.instance);
    _tabController = TabController(length: 2, vsync: this);
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    final session = await _sessionStore.restore();
    if (!mounted) {
      return;
    }
    setState(() {
      _session = session;
    });
    if (session != null) {
      await _reloadAll();
    } else {
      setState(() {
        _loading = false;
      });
    }
  }

  Future<void> _reloadAll() async {
    final current = _session;
    if (current == null) {
      setState(() {
        _loading = false;
      });
      return;
    }

    setState(() {
      _loading = true;
    });

    final location = await _getCurrentLocation(silent: true);
    final active = await _repository.getActive(current.userId);
    final removed = await _repository.getRemoved(current.userId);
    final count = await _repository.countAll(current.userId);

    if (!mounted) {
      return;
    }

    setState(() {
      _currentLocation = location;
      _active = _sortByDistance(active, location);
      _removed = removed;
      _registeredCount = count;
      _loading = false;
    });

    _processNearbyAlerts();
  }

  Future<Position?> _getCurrentLocation({bool silent = false}) async {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      if (!silent && mounted) {
        _showMessage('Lokationstjenester er slukket.');
      }
      return null;
    }

    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }

    if (permission == LocationPermission.denied ||
        permission == LocationPermission.deniedForever) {
      if (!silent && mounted) {
        _showMessage('Kamera- og lokationsadgang kraeves.');
      }
      return null;
    }

    try {
      return await Geolocator.getCurrentPosition(locationSettings: const LocationSettings(accuracy: LocationAccuracy.high));
    } catch (_) {
      if (!silent && mounted) {
        _showMessage('Kunne ikke hente GPS-position. Proev igen udendoers.');
      }
      return null;
    }
  }

  List<Plakat> _sortByDistance(List<Plakat> items, Position? location) {
    if (location == null) {
      return List<Plakat>.from(items);
    }

    final sorted = List<Plakat>.from(items);
    sorted.sort((a, b) {
      final da = _distanceMeters(location, a);
      final db = _distanceMeters(location, b);
      return da.compareTo(db);
    });
    return sorted;
  }

  double _distanceMeters(Position location, Plakat item) {
    return Geolocator.distanceBetween(
      location.latitude,
      location.longitude,
      item.latitude,
      item.longitude,
    );
  }

  void _processNearbyAlerts() {
    final location = _currentLocation;
    if (location == null) {
      return;
    }

    for (final item in _active) {
      final id = item.id;
      if (id == null) {
        continue;
      }

      final distance = _distanceMeters(location, item);
      final wasAlerted = _nearbyAlertState[id] == true;

      if (distance <= removeAllowedDistanceMeters && !wasAlerted) {
        _nearbyAlertState[id] = true;
        HapticFeedback.mediumImpact();
        _showMessage('Du er taet paa en plakat. Husk at markere nedtaget.');
      } else if (distance > removeAlertResetDistanceMeters && wasAlerted) {
        _nearbyAlertState[id] = false;
      }
    }
  }

  Future<void> _loginWithEmail() async {
    try {
      final session = await _sessionStore.loginWithEmail(
        _emailController.text.trim(),
        _passwordController.text,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _session = session;
        _loginError = null;
      });
      await _reloadAll();
    } catch (e) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loginError = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _loginWithGooglePlaceholder() async {
    final session = await _sessionStore.loginWithGooglePlaceholder();
    if (!mounted) {
      return;
    }
    setState(() {
      _session = session;
      _loginError = null;
    });
    await _reloadAll();
  }

  Future<void> _activateBilling() async {
    final updated = await _sessionStore.setBillingActive(true);
    if (!mounted || updated == null) {
      return;
    }
    setState(() {
      _session = updated;
    });
  }

  Future<void> _logout() async {
    await _sessionStore.clear();
    if (!mounted) {
      return;
    }
    setState(() {
      _session = null;
      _active = const <Plakat>[];
      _removed = const <Plakat>[];
      _registeredCount = 0;
      _loading = false;
      _currentLocation = null;
      _nearbyAlertState.clear();
    });
  }

  Future<Uint8List?> _captureImageBytes() async {
    try {
      final image = await _imagePicker.pickImage(
        source: ImageSource.camera,
        imageQuality: 80,
      );
      if (image == null) {
        _showMessage('Kamera blev annulleret.');
        return null;
      }
      return await image.readAsBytes();
    } catch (_) {
      _showMessage('Kunne ikke aabne kameraet.');
      return null;
    }
  }

  Future<void> _addPoster() async {
    final session = _session;
    if (session == null) {
      return;
    }
    if (!session.billingActive) {
      _showMessage('Aktiveer betaling foerst.');
      return;
    }

    final imageBytes = await _captureImageBytes();
    if (imageBytes == null) {
      return;
    }

    final location = await _getCurrentLocation();
    if (location == null) {
      return;
    }

    await _repository.insert(
      Plakat(
        ownerUserId: session.userId,
        createdAtMillis: DateTime.now().millisecondsSinceEpoch,
        latitude: location.latitude,
        longitude: location.longitude,
        imageJpeg: imageBytes,
      ),
    );

    await _reloadAll();
    _showMessage('Plakat gemt.');
  }

  Future<void> _markRemoved(Plakat item) async {
    final id = item.id;
    if (id == null) {
      return;
    }

    final location = await _getCurrentLocation();
    if (location == null) {
      return;
    }

    final distance = _distanceMeters(location, item);
    if (distance > removeAllowedDistanceMeters) {
      _showMessage('Du skal vaere inden for ${removeAllowedDistanceMeters.toInt()} m.');
      return;
    }

    final proofImage = await _captureImageBytes();
    if (proofImage == null) {
      return;
    }

    await _repository.markAsRemoved(
      id: id,
      removedAtMillis: DateTime.now().millisecondsSinceEpoch,
      removedLatitude: location.latitude,
      removedLongitude: location.longitude,
      removalImageJpeg: proofImage,
    );

    await _reloadAll();
    _showMessage('Plakat markeret som nedtaget med bevis.');
  }

  Future<void> _deletePoster(Plakat item) async {
    await _repository.delete(item);
    await _reloadAll();
  }

  Future<void> _openRoute(Plakat item) async {
    final googleUri = Uri.parse(
      'https://www.google.com/maps/dir/?api=1&destination=${item.latitude},${item.longitude}',
    );
    final appleUri = Uri.parse('http://maps.apple.com/?daddr=${item.latitude},${item.longitude}');

    if (await canLaunchUrl(googleUri)) {
      await launchUrl(googleUri, mode: LaunchMode.externalApplication);
      return;
    }
    if (await canLaunchUrl(appleUri)) {
      await launchUrl(appleUri, mode: LaunchMode.externalApplication);
      return;
    }

    _showMessage('Ingen kortapp fundet til rutevejledning.');
  }

  void _showMessage(String message) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  String _distanceText(Plakat item) {
    final location = _currentLocation;
    if (location == null) {
      return 'afstand ukendt';
    }
    final distance = _distanceMeters(location, item);
    if (distance < 1000) {
      return 'afstand ${distance.round()} m';
    }
    return 'afstand ${(distance / 1000).toStringAsFixed(2)} km';
  }

  String _formatDate(int millis) {
    final date = DateTime.fromMillisecondsSinceEpoch(millis);
    return '${date.day.toString().padLeft(2, '0')}-${date.month.toString().padLeft(2, '0')}-${date.year} '
        '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  }

  String _formatKroner(int registeredCount) {
    final total = 500.0 + (registeredCount * 0.15);
    return '${total.toStringAsFixed(2)} kr';
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = _session;
    if (session == null) {
      return _LoginView(
        emailController: _emailController,
        passwordController: _passwordController,
        loginError: _loginError,
        onEmailLogin: _loginWithEmail,
        onGoogleLogin: _loginWithGooglePlaceholder,
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Husk plakaten'),
        actions: <Widget>[
          IconButton(
            onPressed: _logout,
            icon: const Icon(Icons.logout),
            tooltip: 'Log ud',
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          tabs: <Widget>[
            Tab(text: 'Aktive (${_active.length})'),
            Tab(text: 'Nedtagne (${_removed.length})'),
          ],
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text('Logget ind som: ${session.email}'),
                      Text(
                        session.billingActive
                            ? 'betaling: aktiv'
                            : 'betaling: ikke aktiv',
                      ),
                      Text(
                        'registreret: $_registeredCount plakater Ã‚Â· estimeret pris: ${_formatKroner(_registeredCount)}',
                      ),
                      const SizedBox(height: 8),
                      if (session.billingActive)
                        FilledButton(
                          onPressed: _addPoster,
                          child: const Text('Tag billede af plakat'),
                        )
                      else
                        FilledButton(
                          onPressed: _activateBilling,
                          child: const Text('Aktiver betaling'),
                        ),
                      TextButton(
                        onPressed: _reloadAll,
                        child: const Text('Opdater afstande'),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: TabBarView(
                    controller: _tabController,
                    children: <Widget>[
                      _PosterList(
                        items: _active,
                        emptyText: 'Ingen aktive plakater endnu.',
                        distanceText: _distanceText,
                        canMarkRemoved: (item) {
                          final loc = _currentLocation;
                          if (loc == null) {
                            return true;
                          }
                          return _distanceMeters(loc, item) <= removeAllowedDistanceMeters;
                        },
                        showMarkRemoved: session.billingActive,
                        onDelete: _deletePoster,
                        onMarkRemoved: _markRemoved,
                        onNavigate: _openRoute,
                        formatDate: _formatDate,
                      ),
                      _PosterList(
                        items: _removed,
                        emptyText: 'Ingen nedtagne plakater endnu.',
                        distanceText: (_) => 'Nedtaget',
                        canMarkRemoved: (_) => false,
                        showMarkRemoved: false,
                        onDelete: _deletePoster,
                        onMarkRemoved: _markRemoved,
                        onNavigate: _openRoute,
                        formatDate: _formatDate,
                        removedList: true,
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }
}

class _LoginView extends StatelessWidget {
  const _LoginView({
    required this.emailController,
    required this.passwordController,
    required this.loginError,
    required this.onEmailLogin,
    required this.onGoogleLogin,
  });

  final TextEditingController emailController;
  final TextEditingController passwordController;
  final String? loginError;
  final Future<void> Function() onEmailLogin;
  final Future<void> Function() onGoogleLogin;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Log ind')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: <Widget>[
            TextField(
              controller: emailController,
              decoration: const InputDecoration(labelText: 'Email'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: passwordController,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Kodeord'),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: onEmailLogin,
              child: const Text('Log ind med email'),
            ),
            TextButton(
              onPressed: onGoogleLogin,
              child: const Text('Log ind med Google (midlertidig)'),
            ),
            if (loginError != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(loginError!),
              ),
            const SizedBox(height: 12),
            const Text(
              'Firebase er ikke konfigureret endnu. Denne login er lokal indtil projektet kobles paa Firebase.',
            ),
          ],
        ),
      ),
    );
  }
}

class _PosterList extends StatelessWidget {
  const _PosterList({
    required this.items,
    required this.emptyText,
    required this.distanceText,
    required this.canMarkRemoved,
    required this.showMarkRemoved,
    required this.onDelete,
    required this.onMarkRemoved,
    required this.onNavigate,
    required this.formatDate,
    this.removedList = false,
  });

  final List<Plakat> items;
  final String emptyText;
  final String Function(Plakat item) distanceText;
  final bool Function(Plakat item) canMarkRemoved;
  final bool showMarkRemoved;
  final Future<void> Function(Plakat item) onDelete;
  final Future<void> Function(Plakat item) onMarkRemoved;
  final Future<void> Function(Plakat item) onNavigate;
  final String Function(int millis) formatDate;
  final bool removedList;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return Center(child: Text(emptyText));
    }

    return ListView.builder(
      itemCount: items.length,
      itemBuilder: (BuildContext context, int index) {
        final item = items[index];
        final canRemove = canMarkRemoved(item);
        final image = (removedList && item.removalImageJpeg != null)
            ? item.removalImageJpeg
            : item.imageJpeg;

        return Card(
          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Padding(
            padding: const EdgeInsets.all(8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                if (image != null)
                  GestureDetector(
                    onTap: () => _showDetailsDialog(context, item, distanceText(item), formatDate),
                    child: Image.memory(
                      Uint8List.fromList(image),
                      height: 130,
                      width: double.infinity,
                      fit: BoxFit.cover,
                    ),
                  ),
                const SizedBox(height: 8),
                Text('GPS: ${item.latitude.toStringAsFixed(5)}, ${item.longitude.toStringAsFixed(5)}'),
                Text('Oprettet: ${formatDate(item.createdAtMillis)}'),
                if (item.removedAtMillis != null)
                  Text('Nedtaget: ${formatDate(item.removedAtMillis!)}'),
                Text(distanceText(item)),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 8,
                  children: <Widget>[
                    OutlinedButton(
                      onPressed: () => onNavigate(item),
                      child: const Text('Rutevejledning'),
                    ),
                    if (showMarkRemoved)
                      FilledButton.tonal(
                        onPressed: canRemove ? () => onMarkRemoved(item) : null,
                        child: const Text('Markeer nedtaget'),
                      ),
                    TextButton(
                      onPressed: () => onDelete(item),
                      child: const Text('Slet'),
                    ),
                  ],
                ),
                if (showMarkRemoved && !canRemove)
                  const Text('Du skal vaere taettere paa plakaten (max 10 m).'),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showDetailsDialog(
    BuildContext context,
    Plakat item,
    String distance,
    String Function(int millis) formatDate,
  ) {
    showDialog<void>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Plakat detaljer'),
          content: Text(
            'Oprettet: ${formatDate(item.createdAtMillis)}\n'
            'GPS: ${item.latitude.toStringAsFixed(5)}, ${item.longitude.toStringAsFixed(5)}\n'
            '$distance',
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Luk'),
            ),
          ],
        );
      },
    );
  }
}