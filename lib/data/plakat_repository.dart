import 'package:husk_plakaten_flutter/data/plakat_database.dart';
import 'package:husk_plakaten_flutter/models/plakat.dart';

class PlakatRepository {
  const PlakatRepository(this._database);

  final PlakatDatabase _database;

  Future<List<Plakat>> getActive(String ownerUserId) async {
    final db = await _database.database;
    final rows = await db.query(
      'plakater',
      where: 'ownerUserId = ? AND isRemoved = 0',
      whereArgs: <Object?>[ownerUserId],
      orderBy: 'createdAtMillis DESC',
    );
    return rows.map(Plakat.fromMap).toList();
  }

  Future<List<Plakat>> getRemoved(String ownerUserId) async {
    final db = await _database.database;
    final rows = await db.query(
      'plakater',
      where: 'ownerUserId = ? AND isRemoved = 1',
      whereArgs: <Object?>[ownerUserId],
      orderBy: 'removedAtMillis DESC, createdAtMillis DESC',
    );
    return rows.map(Plakat.fromMap).toList();
  }

  Future<int> countAll(String ownerUserId) async {
    final db = await _database.database;
    final rows = await db.rawQuery(
      'SELECT COUNT(*) AS total FROM plakater WHERE ownerUserId = ?',
      <Object?>[ownerUserId],
    );
    return (rows.first['total'] as int?) ?? 0;
  }

  Future<void> insert(Plakat item) async {
    final db = await _database.database;
    await db.insert('plakater', item.toMap());
  }

  Future<void> delete(Plakat item) async {
    if (item.id == null) {
      return;
    }
    final db = await _database.database;
    await db.delete(
      'plakater',
      where: 'id = ?',
      whereArgs: <Object?>[item.id],
    );
  }

  Future<void> markAsRemoved({
    required int id,
    required int removedAtMillis,
    required double removedLatitude,
    required double removedLongitude,
    List<int>? removalImageJpeg,
  }) async {
    final db = await _database.database;
    await db.update(
      'plakater',
      <String, Object?>{
        'isRemoved': 1,
        'removedAtMillis': removedAtMillis,
        'removedLatitude': removedLatitude,
        'removedLongitude': removedLongitude,
        'removalImageJpeg': removalImageJpeg,
      },
      where: 'id = ?',
      whereArgs: <Object?>[id],
    );
  }
}
