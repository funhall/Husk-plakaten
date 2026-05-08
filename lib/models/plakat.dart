class Plakat {
  const Plakat({
    this.id,
    required this.ownerUserId,
    required this.createdAtMillis,
    required this.latitude,
    required this.longitude,
    this.imageJpeg,
    this.qrText,
    this.isRemoved = false,
    this.removedAtMillis,
    this.removedLatitude,
    this.removedLongitude,
    this.removalImageJpeg,
  });

  final int? id;
  final String ownerUserId;
  final int createdAtMillis;
  final double latitude;
  final double longitude;
  final List<int>? imageJpeg;
  final String? qrText;
  final bool isRemoved;
  final int? removedAtMillis;
  final double? removedLatitude;
  final double? removedLongitude;
  final List<int>? removalImageJpeg;

  Plakat copyWith({
    int? id,
    String? ownerUserId,
    int? createdAtMillis,
    double? latitude,
    double? longitude,
    List<int>? imageJpeg,
    String? qrText,
    bool? isRemoved,
    int? removedAtMillis,
    double? removedLatitude,
    double? removedLongitude,
    List<int>? removalImageJpeg,
  }) {
    return Plakat(
      id: id ?? this.id,
      ownerUserId: ownerUserId ?? this.ownerUserId,
      createdAtMillis: createdAtMillis ?? this.createdAtMillis,
      latitude: latitude ?? this.latitude,
      longitude: longitude ?? this.longitude,
      imageJpeg: imageJpeg ?? this.imageJpeg,
      qrText: qrText ?? this.qrText,
      isRemoved: isRemoved ?? this.isRemoved,
      removedAtMillis: removedAtMillis ?? this.removedAtMillis,
      removedLatitude: removedLatitude ?? this.removedLatitude,
      removedLongitude: removedLongitude ?? this.removedLongitude,
      removalImageJpeg: removalImageJpeg ?? this.removalImageJpeg,
    );
  }

  Map<String, Object?> toMap() {
    return <String, Object?>{
      'id': id,
      'ownerUserId': ownerUserId,
      'createdAtMillis': createdAtMillis,
      'latitude': latitude,
      'longitude': longitude,
      'imageJpeg': imageJpeg,
      'qrText': qrText,
      'isRemoved': isRemoved ? 1 : 0,
      'removedAtMillis': removedAtMillis,
      'removedLatitude': removedLatitude,
      'removedLongitude': removedLongitude,
      'removalImageJpeg': removalImageJpeg,
    };
  }

  factory Plakat.fromMap(Map<String, Object?> map) {
    return Plakat(
      id: map['id'] as int?,
      ownerUserId: map['ownerUserId'] as String? ?? '',
      createdAtMillis: map['createdAtMillis'] as int? ?? 0,
      latitude: (map['latitude'] as num?)?.toDouble() ?? 0,
      longitude: (map['longitude'] as num?)?.toDouble() ?? 0,
      imageJpeg: map['imageJpeg'] as List<int>?,
      qrText: map['qrText'] as String?,
      isRemoved: (map['isRemoved'] as int? ?? 0) == 1,
      removedAtMillis: map['removedAtMillis'] as int?,
      removedLatitude: (map['removedLatitude'] as num?)?.toDouble(),
      removedLongitude: (map['removedLongitude'] as num?)?.toDouble(),
      removalImageJpeg: map['removalImageJpeg'] as List<int>?,
    );
  }
}
