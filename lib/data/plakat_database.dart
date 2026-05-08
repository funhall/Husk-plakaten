import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

class PlakatDatabase {
  static final PlakatDatabase instance = PlakatDatabase._();
  PlakatDatabase._();

  Database? _database;

  Future<Database> get database async {
    if (_database != null) {
      return _database!;
    }
    _database = await _open();
    return _database!;
  }

  Future<Database> _open() async {
    final dbPath = await getDatabasesPath();
    final path = p.join(dbPath, 'husk_plakaten.db');

    return openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE plakater (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ownerUserId TEXT NOT NULL DEFAULT '',
            createdAtMillis INTEGER NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            imageJpeg BLOB NULL,
            qrText TEXT NULL,
            isRemoved INTEGER NOT NULL DEFAULT 0,
            removedAtMillis INTEGER NULL,
            removedLatitude REAL NULL,
            removedLongitude REAL NULL,
            removalImageJpeg BLOB NULL
          )
        ''');
      },
    );
  }
}
