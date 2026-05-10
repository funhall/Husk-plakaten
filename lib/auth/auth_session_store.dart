import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class AuthSession {
  const AuthSession({
    required this.userId,
    required this.email,
    required this.billingActive,
  });

  final String userId;
  final String email;
  final bool billingActive;

  AuthSession copyWith({
    String? userId,
    String? email,
    bool? billingActive,
  }) {
    return AuthSession(
      userId: userId ?? this.userId,
      email: email ?? this.email,
      billingActive: billingActive ?? this.billingActive,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'userId': userId,
      'email': email,
      'billingActive': billingActive,
    };
  }

  factory AuthSession.fromMap(Map<String, dynamic> map) {
    return AuthSession(
      userId: map['userId'] as String? ?? '',
      email: map['email'] as String? ?? '',
      billingActive: map['billingActive'] as bool? ?? false,
    );
  }
}

class AuthSessionStore {
  static const String _sessionKey = 'auth_session_v1';

  Future<AuthSession?> restore() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_sessionKey);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      final session = AuthSession.fromMap(decoded);
      if (session.userId.isEmpty || session.email.isEmpty) {
        return null;
      }
      return session;
    } catch (_) {
      return null;
    }
  }

  Future<AuthSession> loginWithEmail(String email, String password) async {
    if (!email.contains('@')) {
      throw Exception('Ugyldig email.');
    }
    if (password.length < 6) {
      throw Exception('Kodeord skal mindst være 6 tegn.');
    }

    final normalizedEmail = email.trim();
    final userId = 'email_${normalizedEmail.toLowerCase()}';
    final existing = await restore();
    final session = AuthSession(
      userId: userId,
      email: normalizedEmail,
      billingActive: existing?.billingActive ?? false,
    );
    await _save(session);
    return session;
  }

  Future<AuthSession> loginWithGooglePlaceholder() async {
    final existing = await restore();
    final session = AuthSession(
      userId: 'google_${DateTime.now().millisecondsSinceEpoch}',
      email: 'google.user@placeholder.local',
      billingActive: existing?.billingActive ?? false,
    );
    await _save(session);
    return session;
  }

  Future<AuthSession?> setBillingActive(bool active) async {
    final existing = await restore();
    if (existing == null) {
      return null;
    }
    final updated = existing.copyWith(billingActive: active);
    await _save(updated);
    return updated;
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_sessionKey);
  }

  Future<void> _save(AuthSession session) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_sessionKey, jsonEncode(session.toMap()));
  }
}