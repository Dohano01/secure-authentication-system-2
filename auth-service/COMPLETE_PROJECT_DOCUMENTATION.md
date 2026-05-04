# 📚 Documentation Complète - Système d'Authentification Sécurisé

## 📋 Table des Matières

1. [Vue d'Ensemble du Projet](#vue-densemble-du-projet)
2. [Architecture du Système](#architecture-du-système)
3. [Couches de l'Application](#couches-de-lapplication)
4. [Fonctionnalités de Sécurité](#fonctionnalités-de-sécurité)
5. [Endpoints API](#endpoints-api)
6. [Base de Données](#base-de-données)
7. [Flux d'Authentification](#flux-dauthentification)
8. [Sécurité et Protection](#sécurité-et-protection)
9. [Tests et Validation](#tests-et-validation)

---

# Vue d'Ensemble du Projet

## 🎯 Objectif

Développer un système d'authentification moderne et sécurisé pour une architecture microservices, implémentant les meilleures pratiques de sécurité pour protéger contre les attaques courantes.

## 🛠️ Technologies Utilisées

- **Framework:** Spring Boot 3.5.7
- **Langage:** Java 17
- **Base de Données:** PostgreSQL
- **Sécurité:** Spring Security, JWT, Argon2, TOTP
- **ORM:** Hibernate/JPA
- **Documentation:** OpenAPI/Swagger

## 📊 Statistiques du Projet

- **Endpoints:** 15+
- **Entités:** 4 (User, Role, RefreshToken, SecurityAuditLog)
- **Services:** 5 (UserService, SecurityAuditService, RefreshTokenService, MfaService, MfaTokenService)
- **Fonctionnalités de Sécurité:** 6 principales
- **Lignes de Code:** ~2000+

---

# Architecture du Système

## 🏗️ Architecture en Couches

```
┌─────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                    │
│                  (Controllers / REST API)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ AuthController│  │ (Future API) │  │ (Future API) │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                     BUSINESS LAYER                       │
│                      (Services)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ UserService  │  │RefreshToken  │  │  MfaService  │  │
│  │              │  │   Service    │  │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│  ┌──────────────┐  ┌──────────────┐                    │
│  │SecurityAudit │  │MfaToken      │                    │
│  │   Service    │  │   Service    │                    │
│  └──────────────┘  └──────────────┘                    │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                      DATA LAYER                          │
│                   (Repositories / JPA)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │UserRepository│  │RefreshToken  │  │SecurityAudit │  │
│  │              │  │  Repository  │  │   Repository │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   DATABASE LAYER                         │
│                    (PostgreSQL)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │    users     │  │refresh_tokens│  │security_audit│  │
│  │              │  │              │  │    _logs     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 🔐 Security Layer (Couche Transversale)

```
┌─────────────────────────────────────────────────────────┐
│                  SECURITY COMPONENTS                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ SecurityConfig│  │   JwtUtil    │  │Argon2Password│  │
│  │               │  │              │  │   Encoder    │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

# Couches de l'Application

## 1. 📱 Présentation Layer (Controllers)

### Rôle
Gère les requêtes HTTP entrantes, valide les données, et retourne les réponses appropriées.

### Composants

#### `AuthController.java`
**Rôle:** Point d'entrée principal pour toutes les opérations d'authentification.

**Responsabilités:**
- Gérer les requêtes HTTP (GET, POST)
- Extraire les informations de la requête (IP, User-Agent)
- Valider les tokens JWT
- Orchestrer les appels aux services
- Retourner les réponses HTTP formatées

**Localisation:** `com.example.auth_service.controller`

**Annotations:**
- `@RestController` - Indique que c'est un contrôleur REST
- `@RequestMapping("/auth")` - Préfixe pour tous les endpoints

---

## 2. 💼 Business Layer (Services)

### Rôle
Contient la logique métier, les règles de sécurité, et orchestre les opérations entre les différentes couches.

### Composants

#### `UserService.java`
**Rôle:** Gestion des utilisateurs et de leur sécurité.

**Responsabilités:**
- Inscription de nouveaux utilisateurs
- Gestion des tentatives de connexion échouées
- Verrouillage/déverrouillage de comptes
- Gestion des versions de tokens

**Méthodes Clés:**
- `register()` - Créer un nouvel utilisateur
- `handleFailedLogin()` - Gérer les échecs de connexion
- `handleSuccessfulLogin()` - Réinitialiser les compteurs après succès
- `isAccountLocked()` - Vérifier le statut de verrouillage
- `unlockAccount()` - Déverrouiller manuellement un compte

**Sécurité:**
- Hashage des mots de passe avec Argon2
- Comptage des tentatives échouées
- Gestion automatique du verrouillage

#### `SecurityAuditService.java`
**Rôle:** Journalisation de tous les événements de sécurité.

**Responsabilités:**
- Logger les tentatives de connexion (succès/échec)
- Logger les verrouillages de compte
- Logger les opérations MFA
- Logger les opérations de tokens
- Fournir l'historique de sécurité

**Méthodes Clés:**
- `logLoginSuccess()` - Logger connexion réussie
- `logLoginFailed()` - Logger connexion échouée
- `logAccountLocked()` - Logger verrouillage
- `getUserLoginHistory()` - Récupérer l'historique

**Sécurité:**
- Traçabilité complète de toutes les actions
- Détection d'anomalies possible
- Conformité aux réglementations

#### `RefreshTokenService.java`
**Rôle:** Gestion des refresh tokens pour la rotation des tokens.

**Responsabilités:**
- Générer des refresh tokens uniques
- Valider les refresh tokens
- Révoquer des tokens individuels ou en masse
- Gérer l'expiration des tokens
- Nettoyer les tokens expirés

**Méthodes Clés:**
- `createRefreshToken()` - Créer un nouveau token
- `validateRefreshToken()` - Valider un token
- `revokeToken()` - Révoquer un token
- `revokeAllUserTokens()` - Révoquer tous les tokens d'un utilisateur

**Sécurité:**
- Tokens stockés en base de données (révocables)
- Expiration automatique
- Versioning pour invalidation globale

#### `MfaService.java`
**Rôle:** Gestion de l'authentification multi-facteurs (MFA).

**Responsabilités:**
- Générer des secrets TOTP
- Générer des QR codes pour configuration
- Valider les codes TOTP
- Activer/désactiver MFA

**Méthodes Clés:**
- `generateSecret()` - Générer un secret unique
- `generateQrCodeDataUrl()` - Créer QR code
- `verifyCode()` - Vérifier un code TOTP
- `enableMfa()` / `disableMfa()` - Gérer l'état MFA

**Sécurité:**
- Codes TOTP à 6 chiffres
- Fenêtre de tolérance pour synchronisation
- Secrets stockés de manière sécurisée

#### `MfaTokenService.java`
**Rôle:** Gestion des tokens temporaires pendant le flux de login MFA.

**Responsabilités:**
- Générer des tokens temporaires (valides 5 minutes)
- Valider les tokens MFA
- Nettoyer les tokens expirés

**Sécurité:**
- Tokens en mémoire (non persistés)
- Expiration courte (5 minutes)
- Nettoyage automatique

---

## 3. 💾 Data Layer (Repositories)

### Rôle
Abstraction de l'accès aux données, utilisation de JPA/Hibernate pour les opérations CRUD.

### Composants

#### `UserRepository.java`
**Interface JPA pour les opérations sur les utilisateurs:**
- `findByUsername()` - Trouver par nom d'utilisateur
- `existsByUsername()` - Vérifier l'existence

#### `RefreshTokenRepository.java`
**Interface JPA pour les refresh tokens:**
- `findByToken()` - Trouver par token
- `findByUser()` - Trouver tous les tokens d'un utilisateur
- `deleteExpiredTokens()` - Nettoyer les tokens expirés
- `revokeAllUserTokens()` - Révoquer tous les tokens

#### `SecurityAuditLogRepository.java`
**Interface JPA pour les logs de sécurité:**
- `findByUsernameOrderByTimestampDesc()` - Historique d'un utilisateur
- `findByUsernameAndEventTypeAndTimestampAfter()` - Filtrage avancé
- `findByIpAddressAndEventTypeAndTimestampAfter()` - Recherche par IP

---

## 4. 🗄️ Database Layer (PostgreSQL)

### Rôle
Stockage persistant de toutes les données.

### Tables

#### `users`
Stocke les informations des utilisateurs et leur état de sécurité.

**Colonnes de Sécurité:**
- `failed_login_attempts` - Compteur de tentatives échouées
- `account_locked` - Statut de verrouillage
- `lock_time` - Timestamp du verrouillage
- `token_version` - Version pour invalidation globale
- `mfa_enabled` - Statut MFA
- `mfa_secret` - Secret TOTP

#### `refresh_tokens`
Stocke tous les refresh tokens actifs.

**Colonnes:**
- `token` - UUID du token
- `user_id` - Référence à l'utilisateur
- `expiry_date` - Date d'expiration
- `revoked` - Statut de révocation
- `ip_address` - IP de création
- `device_type` - Type d'appareil

#### `security_audit_logs`
Journal de tous les événements de sécurité.

**Colonnes:**
- `event_type` - Type d'événement
- `username` - Utilisateur concerné
- `ip_address` - Adresse IP
- `user_agent` - User agent
- `timestamp` - Moment de l'événement
- `success` - Succès/échec

---

# Fonctionnalités de Sécurité

## 1. 🔐 Hashage Argon2

### Pourquoi c'était nécessaire?
Les mots de passe doivent être stockés de manière sécurisée. Argon2 est le gagnant du Password Hashing Competition (2015) et est considéré comme le meilleur algorithme actuel.

### Comment ça améliore la sécurité?
- **Résistance aux attaques par force brute:** Argon2 est très lent à calculer
- **Résistance aux attaques par table arc-en-ciel:** Chaque hash est unique grâce au salt
- **Adaptatif:** Peut être configuré pour être plus lent si nécessaire
- **Résistant aux attaques par GPU/ASIC:** Utilise la mémoire de manière intensive

### Implémentation

**Fichier:** `Argon2PasswordEncoder.java`

```java
@Component
public class Argon2PasswordEncoder implements PasswordEncoder {
    private final Argon2 argon2 = Argon2Factory.create(
        Argon2Factory.Argon2Types.ARGON2id,
        16,      // Salt length
        32       // Hash length
    );
    
    @Override
    public String encode(CharSequence rawPassword) {
        return argon2.hash(2, 65536, 1, rawPassword.toString().toCharArray());
    }
    
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return argon2.verify(encodedPassword, rawPassword.toString().toCharArray());
    }
}
```

**Configuration:**
- **Type:** Argon2id (hybride, meilleur équilibre)
- **Memory:** 65536 KB (64 MB)
- **Iterations:** 2
- **Parallelism:** 1

**Intégration:**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return argon2PasswordEncoder;
}
```

---

## 2. 🛡️ Protection Brute-Force

### Pourquoi c'était nécessaire?
Les attaquants tentent souvent de deviner les mots de passe par force brute. Sans protection, un compte peut être compromis rapidement.

### Comment ça améliore la sécurité?
- **Limite les tentatives:** Bloque après 5 tentatives
- **Délai de verrouillage:** 15 minutes empêchent les attaques automatisées
- **Messages informatifs:** L'utilisateur sait combien de tentatives restent
- **Auto-déverrouillage:** Pas besoin d'intervention admin

### Implémentation

**Fichier:** `User.java`

```java
public void incrementFailedAttempts() {
    this.failedLoginAttempts++;
    this.lastFailedLogin = LocalDateTime.now();
    
    // Lock account after 5 failed attempts
    if (this.failedLoginAttempts >= 5) {
        this.accountLocked = true;
        this.lockTime = LocalDateTime.now();
    }
}

public boolean isAccountNonLocked() {
    if (!accountLocked) {
        return true;
    }
    
    // Auto-unlock after 15 minutes
    if (lockTime != null && LocalDateTime.now().isAfter(lockTime.plusMinutes(15))) {
        this.accountLocked = false;
        this.failedLoginAttempts = 0;
        this.lockTime = null;
        return true;
    }
    
    return false;
}
```

**Fichier:** `AuthController.java`

```java
// Check if account is locked
if (!user.isAccountNonLocked()) {
    long remainingMinutes = userService.getRemainingLockTime(request.getUsername());
    return ResponseEntity.status(423).body(Map.of(
        "error", "Account locked",
        "message", "Too many failed login attempts. Account is locked.",
        "remainingLockTimeMinutes", remainingMinutes
    ));
}
```

**Logique:**
1. Chaque échec incrémente `failed_login_attempts`
2. À 5 tentatives, `account_locked = true` et `lock_time` est défini
3. Le compte est automatiquement déverrouillé après 15 minutes
4. Les tentatives sont réinitialisées après un login réussi

---

## 3. 🔄 Système de Tokens JWT

### Pourquoi c'était nécessaire?
Les tokens JWT permettent une authentification stateless, scalable, et sécurisée sans sessions serveur.

### Comment ça améliore la sécurité?
- **Stateless:** Pas de sessions serveur à gérer
- **Signature cryptographique:** Impossible de falsifier
- **Expiration:** Tokens expirent automatiquement
- **Versioning:** Invalidation globale possible
- **Claims personnalisés:** Rôles et métadonnées inclus

### Implémentation

**Fichier:** `JwtUtil.java`

```java
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration-ms}")
    private long expirationMs;
    
    public String generateToken(String username, List<String> roles, Integer tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        if (tokenVersion != null) {
            claims.put("tokenVersion", tokenVersion);
        }
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(username)
            .setIssuedAt(new Date(now))
            .setExpiration(new Date(now + expirationMs))
            .signWith(SignatureAlgorithm.HS512, secret.getBytes())
            .compact();
    }
}
```

**Configuration:**
- **Algorithme:** HS512 (HMAC avec SHA-512)
- **Expiration:** 1 heure (3600000 ms)
- **Secret:** 256 bits (stocké dans `application.properties`)

**Claims inclus:**
- `sub` (subject): Username
- `roles`: Liste des rôles
- `tokenVersion`: Version pour invalidation
- `iat` (issued at): Date d'émission
- `exp` (expiration): Date d'expiration

---

## 4. 🔄 Refresh Tokens

### Pourquoi c'était nécessaire?
Les access tokens expirent rapidement (sécurité). Les refresh tokens permettent de renouveler les access tokens sans re-saisir les credentials.

### Comment ça améliore la sécurité?
- **Rotation des tokens:** Nouveaux tokens régulièrement
- **Révocable:** Peut être révoqué individuellement ou en masse
- **Tracking:** Toutes les sessions sont trackées
- **Expiration longue:** 30 jours (configurable)
- **Invalidation globale:** Logout de tous les appareils

### Implémentation

**Fichier:** `RefreshToken.java`

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 500)
    private String token; // UUID
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;
    
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion;
    
    @Column(nullable = false)
    private Boolean revoked = false;
}
```

**Génération:**
```java
public RefreshToken createRefreshToken(User user, String ipAddress, 
                                       String userAgent, String deviceType) {
    String token = UUID.randomUUID().toString();
    LocalDateTime expiryDate = LocalDateTime.now().plusDays(30);
    
    return RefreshToken.builder()
        .token(token)
        .user(user)
        .expiryDate(expiryDate)
        .ipAddress(ipAddress)
        .userAgent(userAgent)
        .deviceType(deviceType)
        .tokenVersion(user.getTokenVersion())
        .revoked(false)
        .build();
}
```

**Validation:**
- Vérifie que le token existe
- Vérifie qu'il n'est pas expiré
- Vérifie qu'il n'est pas révoqué
- Vérifie que la version correspond à celle de l'utilisateur

---

## 5. 📝 Audit Logging

### Pourquoi c'était nécessaire?
La traçabilité est essentielle pour la sécurité. Tous les événements doivent être loggés pour détecter les anomalies et répondre aux incidents.

### Comment ça améliore la sécurité?
- **Traçabilité complète:** Tous les événements sont enregistrés
- **Détection d'anomalies:** Patterns suspects identifiables
- **Conformité:** Répond aux exigences réglementaires
- **Forensics:** Analyse post-incident possible
- **Monitoring:** Surveillance en temps réel

### Implémentation

**Fichier:** `SecurityAuditLog.java`

```java
@Entity
@Table(name = "security_audit_logs")
public class SecurityAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "event_type", nullable = false)
    private String eventType; // LOGIN_SUCCESS, LOGIN_FAILED, etc.
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "details", length = 1000)
    private String details;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "success")
    private Boolean success;
}
```

**Types d'événements:**
- `LOGIN_SUCCESS` - Connexion réussie
- `LOGIN_FAILED` - Connexion échouée
- `ACCOUNT_LOCKED` - Compte verrouillé
- `ACCOUNT_UNLOCKED` - Compte déverrouillé
- `MFA_ENABLED` - MFA activé
- `MFA_DISABLED` - MFA désactivé
- `MFA_VERIFICATION_SUCCESS` - Vérification MFA réussie
- `MFA_VERIFICATION_FAILED` - Vérification MFA échouée
- `LOGOUT` - Déconnexion
- `LOGOUT_ALL` - Déconnexion de tous les appareils
- `TOKEN_REFRESH` - Renouvellement de token

**Logging:**
```java
public void logEvent(String username, String eventType, String ipAddress,
                     String userAgent, String details, boolean success) {
    SecurityAuditLog auditLog = SecurityAuditLog.builder()
        .username(username)
        .eventType(eventType)
        .ipAddress(ipAddress)
        .userAgent(userAgent)
        .details(details)
        .success(success)
        .timestamp(LocalDateTime.now())
        .build();
    
    auditLogRepository.save(auditLog);
    
    // Also log to application logs
    log.info("Security Event: {} | User: {} | IP: {} | Success: {}",
        eventType, username, ipAddress, success);
}
```

---

## 6. 🌐 IP Tracking & Device Detection

### Pourquoi c'était nécessaire?
Le tracking IP et la détection d'appareil permettent de détecter les accès suspects et les tentatives de session hijacking.

### Comment ça améliore la sécurité?
- **Détection d'anomalies:** Changements d'IP suspects
- **Session hijacking:** Détection d'accès depuis nouveaux appareils
- **Forensics:** Traçabilité complète des accès
- **Monitoring:** Surveillance des patterns d'accès
- **Alertes:** Notifications sur accès suspects

### Implémentation

**Fichier:** `AuthController.java`

```java
private String getClientIp(HttpServletRequest request) {
    // Try case-insensitive header lookup
    String ip = getHeaderCaseInsensitive(request, "X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        ip = getHeaderCaseInsensitive(request, "X-Real-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
        ip = request.getRemoteAddr();
    }
    // If multiple IPs, take the first one
    if (ip != null && ip.contains(",")) {
        ip = ip.split(",")[0].trim();
    }
    return ip;
}

private String extractDevice(String userAgent) {
    if (userAgent == null) return "Unknown";
    
    userAgent = userAgent.toLowerCase();
    
    if (userAgent.contains("mobile") || userAgent.contains("android") 
        || userAgent.contains("iphone")) {
        return "Mobile";
    } else if (userAgent.contains("tablet") || userAgent.contains("ipad")) {
        return "Tablet";
    } else {
        return "Desktop";
    }
}
```

**Priorité IP:**
1. `X-Forwarded-For` (pour proxies/load balancers)
2. `X-Real-IP` (pour reverse proxies)
3. `request.getRemoteAddr()` (connexion directe)

**Stockage:**
- `users.last_login_ip` - Dernière IP de connexion réussie
- `users.last_login_device` - Dernier appareil utilisé
- `refresh_tokens.ip_address` - IP de création du token
- `security_audit_logs.ip_address` - IP de chaque événement

---

## 7. 🔐 Multi-Factor Authentication (MFA)

### Pourquoi c'était nécessaire?
Même avec un mot de passe fort, un compte peut être compromis. MFA ajoute une couche supplémentaire de sécurité avec un facteur que seul l'utilisateur possède.

### Comment ça améliore la sécurité?
- **Double authentification:** Mot de passe + code TOTP
- **Résistance au phishing:** Le code change toutes les 30 secondes
- **Protection contre le vol:** Même si le mot de passe est volé, le compte reste protégé
- **Conformité:** Répond aux exigences de sécurité élevées
- **Détection d'intrusion:** Tentatives sans code MFA sont bloquées

### Implémentation

**Fichier:** `MfaService.java`

```java
@Service
public class MfaService {
    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;
    
    public String generateSecret() {
        return secretGenerator.generate(); // Génère un secret Base32
    }
    
    public String generateQrCodeDataUrl(String secret, String username) {
        QrData qrData = new QrData.Builder()
            .label(username)
            .secret(secret)
            .issuer(appName)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30) // 30 secondes
            .build();
        
        byte[] qrCodeImage = qrGenerator.generate(qrData);
        String base64Image = Base64.getEncoder().encodeToString(qrCodeImage);
        return "data:image/png;base64," + base64Image;
    }
    
    public boolean verifyCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }
}
```

**Configuration TOTP:**
- **Algorithme:** SHA1
- **Digits:** 6 chiffres
- **Period:** 30 secondes
- **Format:** Base32 secret

**Flux de Login avec MFA:**
1. User entre username/password
2. Si MFA activé → Retourne `mfaToken` temporaire
3. User entre code TOTP
4. Vérification du code
5. Si valide → Génération des tokens finaux

---

# Endpoints API

## 📍 Base URL
```
http://localhost:8081/auth
```

## 🔐 Authentification

### 1. POST /auth/register
**Description:** Inscription d'un nouvel utilisateur

**Request:**
```json
{
  "username": "newuser",
  "password": "SecurePassword123!",
  "email": "user@example.com",
  "fullName": "New User",
  "roles": ["PATIENT"]
}
```

**Response (200 OK):**
```json
{
  "message": "User registered successfully",
  "username": "newuser"
}
```

**Sécurité:**
- Mot de passe hashé avec Argon2
- Validation des données
- Audit logging de l'inscription

**Contraintes:**
- Username doit être unique
- Email optionnel mais recommandé
- Rôles par défaut: PATIENT si non spécifié

---

### 2. POST /auth/login
**Description:** Connexion d'un utilisateur

**Request:**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Response (200 OK) - Sans MFA:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200 OK) - Avec MFA activé:**
```json
{
  "mfaRequired": true,
  "mfaToken": "temporary-token-uuid"
}
```

**Response (401 Unauthorized) - Mauvais mot de passe:**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

**Response (423 Locked) - Compte verrouillé:**
```json
{
  "error": "Account locked",
  "message": "Too many failed login attempts. Account is locked.",
  "remainingLockTimeMinutes": 15,
  "tryAgainIn": "15 minutes"
}
```

**Sécurité:**
- Vérification du mot de passe (Argon2)
- Protection brute-force
- Vérification MFA si activé
- Tracking IP et device
- Audit logging

**Contraintes:**
- Maximum 5 tentatives avant verrouillage
- Verrouillage de 15 minutes
- MFA requis si activé

---

### 3. POST /auth/refresh
**Description:** Renouveler l'access token avec un refresh token

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200 OK):**
```json
{
  "token": "new-access-token-jwt",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid refresh token",
  "message": "Refresh token is invalid, expired, or revoked"
}
```

**Sécurité:**
- Validation du refresh token
- Vérification de la version du token
- Vérification du statut de verrouillage
- Audit logging

**Contraintes:**
- Refresh token doit être valide et non expiré
- Refresh token ne doit pas être révoqué
- Version du token doit correspondre

---

### 4. POST /auth/logout
**Description:** Déconnexion (révoque le refresh token actuel)

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

**Sécurité:**
- Révocation du refresh token
- Audit logging

---

### 5. POST /auth/logout-all
**Description:** Déconnexion de tous les appareils

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "message": "Logged out from all devices successfully",
  "username": "admin"
}
```

**Sécurité:**
- Incrémente `token_version` de l'utilisateur
- Révoque tous les refresh tokens
- Invalide tous les access tokens existants
- Audit logging

**Contraintes:**
- Nécessite un access token valide
- Invalide tous les tokens existants

---

### 6. GET /auth/sessions
**Description:** Liste toutes les sessions actives

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "username": "admin",
  "totalSessions": 2,
  "sessions": [
    {
      "id": 1,
      "deviceType": "Desktop",
      "ipAddress": "203.0.113.45",
      "userAgent": "PostmanRuntime/7.51.0",
      "createdAt": "2026-01-14T00:12:45",
      "expiryDate": "2026-02-13T00:12:45"
    }
  ]
}
```

**Sécurité:**
- Nécessite authentification
- Retourne uniquement les sessions de l'utilisateur connecté

---

### 7. GET /auth/validate
**Description:** Valider un access token

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "tokenVersion": 0,
  "iat": 1768346252,
  "exp": 1768349852
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid token: ..."
}
```

---

### 8. GET /auth/login-history/{username}
**Description:** Historique de sécurité d'un utilisateur

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "username": "admin",
    "eventType": "LOGIN_SUCCESS",
    "ipAddress": "203.0.113.45",
    "userAgent": "PostmanRuntime/7.51.0",
    "details": "User logged in successfully",
    "timestamp": "2026-01-14T00:12:45",
    "success": true
  }
]
```

**Sécurité:**
- Retourne tous les événements de sécurité
- Triés par timestamp (plus récent en premier)

---

### 9. POST /auth/unlock/{username}
**Description:** Déverrouiller manuellement un compte (admin)

**Response (200 OK):**
```json
{
  "message": "Account unlocked successfully",
  "username": "admin"
}
```

**Sécurité:**
- Réinitialise les tentatives
- Audit logging

---

## 🔐 Multi-Factor Authentication (MFA)

### 10. POST /auth/mfa/setup
**Description:** Générer secret et QR code pour MFA

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "secret": "25MYRF4YKZL2IN7PNK77ELHS5U5AQ5IN",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgo...",
  "manualEntryKey": "25MY RF4Y KZL2 IN7P NK77 ELHS 5U5A Q5IN"
}
```

**Sécurité:**
- Nécessite authentification
- Génère un secret unique par utilisateur

**Contraintes:**
- Secret doit être utilisé immédiatement
- QR code en format base64 data URL

---

### 11. POST /auth/mfa/enable-with-secret
**Description:** Activer MFA après vérification du code

**Headers:**
```
Authorization: Bearer <access-token>
```

**Request:**
```json
{
  "secret": "25MYRF4YKZL2IN7PNK77ELHS5U5AQ5IN",
  "code": "123456"
}
```

**Response (200 OK):**
```json
{
  "message": "MFA enabled successfully",
  "username": "admin"
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

**Sécurité:**
- Vérification du code TOTP
- Stockage sécurisé du secret
- Audit logging

**Contraintes:**
- Code doit être valide (6 chiffres)
- Code change toutes les 30 secondes

---

### 12. POST /auth/mfa/disable
**Description:** Désactiver MFA

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "message": "MFA disabled successfully",
  "username": "admin"
}
```

**Sécurité:**
- Supprime le secret
- Audit logging

---

### 13. POST /auth/mfa/verify
**Description:** Vérifier le code MFA pendant le login

**Request:**
```json
{
  "mfaToken": "temporary-token-uuid",
  "code": "123456"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

**Sécurité:**
- Validation du mfaToken temporaire
- Vérification du code TOTP
- Audit logging des tentatives

**Contraintes:**
- mfaToken expire après 5 minutes
- Code doit être valide

---

### 14. GET /auth/mfa/status
**Description:** Vérifier si MFA est activé

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "username": "admin",
  "mfaEnabled": true
}
```

---

# Base de Données

## 📊 Schéma Complet

### Table: `users`
**Rôle:** Stocke les informations des utilisateurs et leur état de sécurité

**Colonnes:**
| Colonne | Type | Description | Sécurité |
|---------|------|-------------|----------|
| `id` | BIGINT | Primary key | - |
| `username` | VARCHAR(255) | Nom d'utilisateur unique | Index unique |
| `password` | VARCHAR(255) | Hash Argon2 | Hashé, jamais en clair |
| `email` | VARCHAR(255) | Email | - |
| `full_name` | VARCHAR(255) | Nom complet | - |
| `failed_login_attempts` | INTEGER | Compteur tentatives | Protection brute-force |
| `account_locked` | BOOLEAN | Statut verrouillage | Protection brute-force |
| `lock_time` | TIMESTAMP | Moment du verrouillage | Auto-unlock |
| `last_login` | TIMESTAMP | Dernière connexion réussie | Tracking |
| `last_failed_login` | TIMESTAMP | Dernier échec | Tracking |
| `token_version` | INTEGER | Version pour invalidation | Rotation tokens |
| `last_login_ip` | VARCHAR(255) | Dernière IP | IP tracking |
| `last_login_device` | VARCHAR(255) | Dernier appareil | Device detection |
| `mfa_enabled` | BOOLEAN | Statut MFA | MFA |
| `mfa_secret` | VARCHAR(255) | Secret TOTP | MFA |

**Index:**
- `username` (unique)

**Relations:**
- Many-to-Many avec `roles` via `user_roles`

---

### Table: `refresh_tokens`
**Rôle:** Stocke tous les refresh tokens actifs

**Colonnes:**
| Colonne | Type | Description | Sécurité |
|---------|------|-------------|----------|
| `id` | BIGSERIAL | Primary key | - |
| `token` | VARCHAR(500) | UUID du token | Index unique |
| `user_id` | BIGINT | Référence utilisateur | Foreign key |
| `expiry_date` | TIMESTAMP | Date d'expiration | Expiration |
| `created_at` | TIMESTAMP | Date de création | Tracking |
| `ip_address` | VARCHAR(45) | IP de création | IP tracking |
| `user_agent` | VARCHAR(500) | User agent | Device tracking |
| `device_type` | VARCHAR(50) | Type d'appareil | Device detection |
| `token_version` | INTEGER | Version du token | Invalidation |
| `revoked` | BOOLEAN | Statut révocation | Révocation |

**Index:**
- `token` (unique)
- `user_id`
- `expiry_date`
- `revoked`

**Relations:**
- Many-to-One avec `users` (CASCADE DELETE)

---

### Table: `security_audit_logs`
**Rôle:** Journal de tous les événements de sécurité

**Colonnes:**
| Colonne | Type | Description | Sécurité |
|---------|------|-------------|----------|
| `id` | BIGSERIAL | Primary key | - |
| `username` | VARCHAR(255) | Utilisateur concerné | Index |
| `event_type` | VARCHAR(255) | Type d'événement | Index |
| `ip_address` | VARCHAR(255) | Adresse IP | IP tracking |
| `user_agent` | VARCHAR(255) | User agent | Device tracking |
| `details` | VARCHAR(1000) | Détails de l'événement | - |
| `timestamp` | TIMESTAMP | Moment de l'événement | Index, tri |
| `success` | BOOLEAN | Succès/échec | - |

**Index:**
- `username`
- `event_type`
- `timestamp`
- `ip_address`

---

### Table: `roles`
**Rôle:** Définit les rôles du système

**Colonnes:**
| Colonne | Type | Description |
|---------|------|-------------|
| `id` | BIGINT | Primary key |
| `name` | VARCHAR(255) | Nom du rôle (unique) |

**Rôles par défaut:**
- `ADMIN` - Administrateur
- `PATIENT` - Patient
- `DOCTOR` - Médecin

---

### Table: `user_roles`
**Rôle:** Table de jointure Many-to-Many

**Colonnes:**
| Colonne | Type | Description |
|---------|------|-------------|
| `user_id` | BIGINT | Foreign key vers users |
| `role_id` | BIGINT | Foreign key vers roles |

---

# Flux d'Authentification

## 🔄 Flux de Login Standard (Sans MFA)

```
┌─────────┐
│  Client │
└────┬────┘
     │ 1. POST /auth/login
     │    { username, password }
     ▼
┌─────────────────┐
│ AuthController  │
└────┬────────────┘
     │ 2. Vérifier user existe
     ▼
┌─────────────────┐
│  UserService    │
└────┬────────────┘
     │ 3. Vérifier compte non verrouillé
     │ 4. Vérifier mot de passe (Argon2)
     │ 5. Vérifier MFA non activé
     ▼
┌─────────────────┐
│ JwtUtil +       │
│ RefreshToken    │
│ Service         │
└────┬────────────┘
     │ 6. Générer access token
     │ 7. Générer refresh token
     │ 8. Logger succès
     ▼
┌─────────┐
│ Client  │ ← { token, refreshToken }
└─────────┘
```

## 🔐 Flux de Login avec MFA

```
┌─────────┐
│  Client │
└────┬────┘
     │ 1. POST /auth/login
     │    { username, password }
     ▼
┌─────────────────┐
│ AuthController  │
└────┬────────────┘
     │ 2. Vérifier credentials
     │ 3. Détecter MFA activé
     ▼
┌─────────────────┐
│ MfaTokenService │
└────┬────────────┘
     │ 4. Générer mfaToken temporaire
     ▼
┌─────────┐
│ Client  │ ← { mfaRequired: true, mfaToken }
└────┬────┘
     │ 5. POST /auth/mfa/verify
     │    { mfaToken, code }
     ▼
┌─────────────────┐
│   MfaService    │
└────┬────────────┘
     │ 6. Vérifier code TOTP
     │ 7. Générer tokens finaux
     ▼
┌─────────┐
│ Client  │ ← { token, refreshToken }
└─────────┘
```

## 🔄 Flux de Refresh Token

```
┌─────────┐
│  Client │
└────┬────┘
     │ 1. POST /auth/refresh
     │    { refreshToken }
     ▼
┌─────────────────┐
│ RefreshToken    │
│ Service         │
└────┬────────────┘
     │ 2. Valider refresh token
     │    - Existe en DB
     │    - Non expiré
     │    - Non révoqué
     │    - Version correcte
     ▼
┌─────────────────┐
│     JwtUtil     │
└────┬────────────┘
     │ 3. Générer nouveau access token
     │ 4. Logger refresh
     ▼
┌─────────┐
│ Client  │ ← { token, refreshToken }
└─────────┘
```

## 🔐 Flux d'Activation MFA

```
┌─────────┐
│  Client │
└────┬────┘
     │ 1. POST /auth/mfa/setup
     │    (avec access token)
     ▼
┌─────────────────┐
│   MfaService    │
└────┬────────────┘
     │ 2. Générer secret TOTP
     │ 3. Générer QR code
     ▼
┌─────────┐
│ Client  │ ← { secret, qrCodeDataUrl, manualEntryKey }
└────┬────┘
     │ 4. Scanner QR ou entrer secret
     │    dans Google Authenticator
     │ 5. Obtenir code TOTP (6 chiffres)
     │ 6. POST /auth/mfa/enable-with-secret
     │    { secret, code }
     ▼
┌─────────────────┐
│   MfaService    │
└────┬────────────┘
     │ 7. Vérifier code
     │ 8. Activer MFA
     │ 9. Logger activation
     ▼
┌─────────┐
│ Client  │ ← { message: "MFA enabled" }
└─────────┘
```

---

# Sécurité et Protection

## 🛡️ Protection Contre les Attaques

### 1. Brute-Force Attack
**Protection:**
- ✅ Limite de 5 tentatives
- ✅ Verrouillage automatique (15 minutes)
- ✅ Compteur par utilisateur
- ✅ Messages informatifs

**Implémentation:**
- Compteur `failed_login_attempts`
- Vérification avant chaque tentative
- Auto-unlock après délai

### 2. Session Hijacking
**Protection:**
- ✅ Tokens JWT avec expiration
- ✅ Refresh tokens révocables
- ✅ IP tracking
- ✅ Device detection
- ✅ Token versioning

**Implémentation:**
- Tracking IP dans chaque token
- Détection d'appareil
- Invalidation globale possible

### 3. Password Cracking
**Protection:**
- ✅ Hashage Argon2 (très lent)
- ✅ Salt unique par mot de passe
- ✅ Résistance GPU/ASIC

**Implémentation:**
- Argon2id avec 64MB mémoire
- 2 itérations
- Hash unique par utilisateur

### 4. Token Theft
**Protection:**
- ✅ Expiration courte (1 heure)
- ✅ Refresh tokens révocables
- ✅ Token versioning
- ✅ Révocation globale

**Implémentation:**
- Access tokens expirent rapidement
- Refresh tokens en base (révocables)
- Version incrémentée = invalidation globale

### 5. Phishing
**Protection:**
- ✅ MFA (codes TOTP)
- ✅ Codes changent toutes les 30 secondes
- ✅ Secret unique par utilisateur

**Implémentation:**
- TOTP avec Google Authenticator
- Secret stocké sécurisé
- Validation stricte

### 6. Account Takeover
**Protection:**
- ✅ MFA requis si activé
- ✅ Audit logging complet
- ✅ Détection d'anomalies (IP, device)
- ✅ Alertes sur changements suspects

**Implémentation:**
- Tous les événements loggés
- Tracking IP et device
- Historique consultable

---

## 🔒 Bonnes Pratiques Implémentées

### 1. Defense in Depth
- ✅ Plusieurs couches de sécurité
- ✅ Protection à chaque niveau
- ✅ Redondance des contrôles

### 2. Principle of Least Privilege
- ✅ Rôles et permissions
- ✅ Accès minimal nécessaire
- ✅ Séparation des responsabilités

### 3. Secure by Default
- ✅ MFA optionnel mais recommandé
- ✅ Verrouillage automatique
- ✅ Expiration par défaut

### 4. Fail Secure
- ✅ Échec = refus d'accès
- ✅ Pas d'informations sensibles dans erreurs
- ✅ Logging des échecs

### 5. Complete Mediation
- ✅ Toutes les requêtes vérifiées
- ✅ Tokens validés à chaque fois
- ✅ Pas de cache de permissions

---

# Tests et Validation

## 🧪 Scénarios de Test

### Test 1: Protection Brute-Force
1. Tenter 5 connexions avec mauvais mot de passe
2. Vérifier que le compte est verrouillé
3. Attendre 15 minutes ou déverrouiller manuellement
4. Vérifier que le compte est accessible

### Test 2: Refresh Token
1. Se connecter et obtenir tokens
2. Utiliser refresh token pour obtenir nouveau access token
3. Révoquer refresh token
4. Vérifier que refresh ne fonctionne plus

### Test 3: MFA
1. Activer MFA
2. Se connecter (devrait demander MFA)
3. Vérifier code MFA
4. Obtenir tokens finaux

### Test 4: Audit Logging
1. Effectuer diverses actions
2. Vérifier que tous les événements sont loggés
3. Consulter l'historique

---

# Conclusion

## ✅ Fonctionnalités Implémentées

1. ✅ Hashage Argon2
2. ✅ Protection brute-force
3. ✅ Tokens JWT avec rotation
4. ✅ Refresh tokens
5. ✅ Audit logging complet
6. ✅ IP tracking et device detection
7. ✅ Multi-Factor Authentication (MFA)

## 🎯 Objectifs Atteints

- ✅ Système d'authentification moderne et sécurisé
- ✅ Protection contre les attaques courantes
- ✅ Traçabilité complète
- ✅ Scalabilité (stateless)
- ✅ Conformité aux meilleures pratiques

## 📈 Métriques de Sécurité

- **Tentatives max:** 5
- **Durée verrouillage:** 15 minutes
- **Expiration access token:** 1 heure
- **Expiration refresh token:** 30 jours
- **Expiration MFA token:** 5 minutes
- **Code TOTP:** 6 chiffres, 30 secondes

---

**Documentation générée le:** 2026-01-14
**Version:** 1.0
**Auteur:** Système d'Authentification Sécurisé
