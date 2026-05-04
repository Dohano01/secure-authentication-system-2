# 🏗️ Documentation d'Architecture - Système d'Authentification Sécurisé

## 📚 Table des Matières

1. [Vue d'Ensemble de l'Architecture](#vue-densemble-de-larchitecture)
2. [Architecture en Couches](#architecture-en-couches)
3. [Détails des Couches](#détails-des-couches)
4. [Flux d'Authentification](#flux-dauthentification)
5. [Flux de Sécurité](#flux-de-sécurité)
6. [Interactions entre Composants](#interactions-entre-composants)
7. [Diagrammes de Séquence](#diagrammes-de-séquence)
8. [Structure des Packages](#structure-des-packages)
9. [Dépendances et Technologies](#dépendances-et-technologies)

---

## 🎯 Vue d'Ensemble de l'Architecture

### Architecture Globale

Le système d'authentification suit une **architecture en couches (Layered Architecture)** avec séparation claire des responsabilités:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT / FRONTEND                            │
│                    (Postman, Web App, Mobile App)                    │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ HTTP/HTTPS
                               │
┌──────────────────────────────▼──────────────────────────────────────┐
│                    PRESENTATION LAYER                                │
│                    (REST Controllers)                                │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    AuthController                               │  │
│  │  - /auth/register  - /auth/login  - /auth/refresh             │  │
│  │  - /auth/logout    - /auth/mfa/*   - /auth/validate            │  │
│  └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ Service Calls
                               │
┌──────────────────────────────▼──────────────────────────────────────┐
│                    BUSINESS LAYER                                    │
│                    (Services)                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │  │
│  │ UserService  │  │RefreshToken │  │  MfaService  │              │  │
│  │              │  │   Service    │  │              │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘              │  │
│  ┌──────────────┐  ┌──────────────┐                                │  │
│  │SecurityAudit│  │MfaToken      │                                │  │
│  │   Service    │  │   Service    │                                │  │
│  └──────────────┘  └──────────────┘                                │  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ Repository Calls
                               │
┌──────────────────────────────▼──────────────────────────────────────┐
│                    DATA ACCESS LAYER                                 │
│                    (Repositories / JPA)                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │  │
│  │UserRepository│  │RefreshToken │  │SecurityAudit │              │  │
│  │              │  │  Repository │  │   Repository │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘              │  │
│  ┌──────────────┐                                                    │  │
│  │RoleRepository│                                                    │  │
│  └──────────────┘                                                    │  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               │ SQL / JPA Queries
                               │
┌──────────────────────────────▼──────────────────────────────────────┐
│                    DATABASE LAYER                                    │
│                    (PostgreSQL)                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │  │
│  │    users     │  │refresh_tokens│  │security_audit│              │  │
│  │              │  │              │  │    _logs     │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘              │  │
│  ┌──────────────┐                                                    │  │
│  │    roles     │                                                    │  │
│  │ user_roles   │                                                    │  │
│  └──────────────┘                                                    │  │
└──────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│              SECURITY LAYER (Transversale)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │  │
│  │ SecurityConfig│  │   JwtUtil    │  │Argon2Password│              │  │
│  │               │  │              │  │   Encoder    │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘              │  │
└─────────────────────────────────────────────────────────────────────┘
```

### Principes d'Architecture

1. **Séparation des Responsabilités (SoC)**
   - Chaque couche a une responsabilité unique et bien définie
   - Pas de mélange entre logique métier et accès aux données

2. **Inversion de Dépendances (DIP)**
   - Les couches supérieures dépendent d'abstractions (interfaces)
   - Les implémentations concrètes sont injectées via Spring DI

3. **Single Responsibility Principle (SRP)**
   - Chaque classe a une seule raison de changer
   - Services spécialisés par domaine

4. **Open/Closed Principle (OCP)**
   - Ouvert à l'extension, fermé à la modification
   - Facile d'ajouter de nouvelles fonctionnalités

---

## 🏛️ Architecture en Couches

### 1. 📱 Présentation Layer (Presentation Layer)

**Rôle:** Point d'entrée de l'application, gestion des requêtes HTTP

**Responsabilités:**
- Recevoir les requêtes HTTP
- Valider les données d'entrée
- Extraire les informations de la requête (IP, User-Agent, headers)
- Orchestrer les appels aux services
- Gérer les réponses HTTP (succès, erreurs)
- Gérer les codes de statut HTTP appropriés

**Composants:**

#### `AuthController.java`
```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    // Endpoints d'authentification
    // Endpoints MFA
    // Endpoints de gestion de tokens
}
```

**Endpoints Principaux:**
- `POST /auth/register` - Inscription
- `POST /auth/login` - Connexion
- `POST /auth/refresh` - Renouvellement de token
- `POST /auth/logout` - Déconnexion
- `POST /auth/mfa/*` - Endpoints MFA
- `GET /auth/validate` - Validation de token
- `GET /auth/sessions` - Liste des sessions

**Flux de Données:**
```
Client Request (JSON)
    ↓
AuthController
    ↓
Validation des données
    ↓
Extraction IP/User-Agent
    ↓
Appel aux Services
    ↓
Formatage de la réponse
    ↓
HTTP Response (JSON)
```

---

### 2. 💼 Business Layer (Service Layer)

**Rôle:** Contient toute la logique métier et les règles de sécurité

**Responsabilités:**
- Implémenter la logique métier
- Appliquer les règles de sécurité
- Orchestrer les opérations entre repositories
- Gérer les transactions
- Valider les données métier
- Gérer les exceptions métier

**Composants:**

#### `UserService.java`
**Responsabilités:**
- Gestion du cycle de vie des utilisateurs
- Gestion des tentatives de connexion
- Gestion du verrouillage de compte
- Hashage des mots de passe
- Validation des credentials

**Méthodes Principales:**
```java
- register() - Inscription d'un utilisateur
- findByUsername() - Recherche d'utilisateur
- handleSuccessfulLogin() - Gestion connexion réussie
- handleFailedLogin() - Gestion connexion échouée
- isAccountLocked() - Vérification verrouillage
- getRemainingLockTime() - Calcul temps restant
- unlockAccount() - Déverrouillage manuel
```

#### `RefreshTokenService.java`
**Responsabilités:**
- Génération de refresh tokens
- Validation de refresh tokens
- Révocation de tokens
- Gestion des sessions utilisateur

**Méthodes Principales:**
```java
- createRefreshToken() - Création d'un token
- validateRefreshToken() - Validation d'un token
- revokeToken() - Révocation d'un token
- revokeAllUserTokens() - Révocation globale
- getUserActiveSessions() - Liste des sessions
```

#### `SecurityAuditService.java`
**Responsabilités:**
- Enregistrement de tous les événements de sécurité
- Recherche dans les logs d'audit
- Génération de rapports

**Méthodes Principales:**
```java
- logEvent() - Log générique
- logLoginSuccess() - Log connexion réussie
- logLoginFailed() - Log connexion échouée
- logAccountLocked() - Log verrouillage
- getUserLoginHistory() - Historique utilisateur
```

#### `MfaService.java`
**Responsabilités:**
- Génération de secrets TOTP
- Génération de QR codes
- Vérification de codes TOTP
- Activation/désactivation MFA

**Méthodes Principales:**
```java
- generateSecret() - Génère secret Base32
- generateQrCodeDataUrl() - Génère QR code
- verifyCode() - Vérifie code TOTP
- enableMfa() - Active MFA
- disableMfa() - Désactive MFA
- isMfaEnabled() - Vérifie statut MFA
```

#### `MfaTokenService.java`
**Responsabilités:**
- Gestion des tokens temporaires pour MFA
- Validation des tokens MFA
- Expiration automatique

**Méthodes Principales:**
```java
- generateMfaToken() - Génère token temporaire
- validateMfaToken() - Valide token
- removeMfaToken() - Supprime token
```

**Flux de Données:**
```
Service Method Call
    ↓
Validation des paramètres
    ↓
Appel aux Repositories
    ↓
Traitement des données
    ↓
Application des règles métier
    ↓
Appel aux autres Services (si nécessaire)
    ↓
Retour du résultat
```

---

### 3. 💾 Data Access Layer (Repository Layer)

**Rôle:** Abstraction de l'accès aux données

**Responsabilités:**
- Fournir une interface pour accéder aux données
- Gérer les requêtes JPA
- Gérer les relations entre entités
- Optimiser les requêtes

**Composants:**

#### `UserRepository.java`
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

#### `RefreshTokenRepository.java`
```java
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserAndRevokedFalse(User user);
    void revokeAllUserTokens(User user);
    void revokeTokensByVersion(User user, Integer tokenVersion);
}
```

#### `SecurityAuditLogRepository.java`
```java
@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {
    List<SecurityAuditLog> findByUsernameOrderByTimestampDesc(String username);
    List<SecurityAuditLog> findByUsernameAndEventTypeAndTimestampAfter(...);
    List<SecurityAuditLog> findByIpAddressAndEventTypeAndTimestampAfter(...);
}
```

**Flux de Données:**
```
Repository Method Call
    ↓
JPA Query Generation
    ↓
SQL Query Execution
    ↓
Result Mapping to Entity
    ↓
Return Entity/Entities
```

---

### 4. 🗄️ Database Layer

**Rôle:** Stockage persistant des données

**Tables Principales:**

#### `users`
```sql
- id (PK)
- username (UNIQUE)
- email (UNIQUE)
- password (Argon2 hash)
- full_name
- failed_login_attempts
- account_locked
- lock_time
- last_login
- last_failed_login
- token_version
- last_login_ip
- last_login_device
- mfa_enabled
- mfa_secret
```

#### `refresh_tokens`
```sql
- id (PK)
- token (UNIQUE, UUID)
- user_id (FK)
- expiry_date
- created_at
- ip_address
- user_agent
- device_type
- token_version
- revoked
```

#### `security_audit_logs`
```sql
- id (PK)
- username
- event_type
- ip_address
- user_agent
- details
- timestamp
- success
```

#### `roles` et `user_roles`
```sql
roles:
- id (PK)
- name (UNIQUE)

user_roles:
- user_id (FK)
- role_id (FK)
```

---

### 5. 🔐 Security Layer (Couche Transversale)

**Rôle:** Composants de sécurité utilisés par toutes les couches

**Composants:**

#### `SecurityConfig.java`
**Responsabilités:**
- Configuration Spring Security
- Définition des règles d'autorisation
- Configuration CSRF
- Configuration des sessions

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        // Configuration des règles de sécurité
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return argon2PasswordEncoder;
    }
}
```

#### `JwtUtil.java`
**Responsabilités:**
- Génération de tokens JWT
- Validation de tokens JWT
- Extraction des claims

```java
@Component
public class JwtUtil {
    public String generateToken(String username, List<String> roles, Integer tokenVersion);
    public Claims validateTokenAndGetClaims(String token);
}
```

#### `Argon2PasswordEncoder.java`
**Responsabilités:**
- Hashage de mots de passe avec Argon2
- Vérification de mots de passe

```java
@Component
public class Argon2PasswordEncoder implements PasswordEncoder {
    public String encode(CharSequence rawPassword);
    public boolean matches(CharSequence rawPassword, String encodedPassword);
}
```

---

## 🔄 Flux d'Authentification

### Flux 1: Inscription (Registration)

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     │ POST /auth/register
     │ { username, password, email, fullName, roles }
     │
     ▼
┌─────────────────┐
│ AuthController  │
│  register()     │
└────┬────────────┘
     │
     │ 1. Extraction IP
     │ 2. Validation données
     │
     ▼
┌─────────────────┐
│  UserService    │
│  register()     │
└────┬────────────┘
     │
     │ 1. Vérifier username/email existe
     │ 2. Hash password (Argon2)
     │ 3. Créer User entity
     │ 4. Assigner roles
     │
     ▼
┌─────────────────┐
│ UserRepository  │
│  save()         │
└────┬────────────┘
     │
     │ INSERT INTO users
     │
     ▼
┌─────────────────┐
│   PostgreSQL    │
│   users table   │
└────┬────────────┘
     │
     │ User créé
     │
     ▼
┌─────────────────┐
│ SecurityAudit   │
│   Service       │
│  logRegistration│
└────┬────────────┘
     │
     │ INSERT INTO security_audit_logs
     │
     ▼
┌─────────────────┐
│   PostgreSQL    │
│ security_audit  │
│    _logs        │
└────┬────────────┘
     │
     │
     ▼
┌─────────┐
│ Client   │
│ 200 OK   │
│ { message, username }
└─────────┘
```

---

### Flux 2: Connexion Simple (Sans MFA)

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     │ POST /auth/login
     │ { username, password }
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ 1. Extraction IP/User-Agent
     │ 2. Vérifier user existe
     │
     ▼
┌─────────────────┐
│  UserService    │
│ findByUsername()│
└────┬────────────┘
     │
     │ SELECT * FROM users WHERE username = ?
     │
     ▼
┌─────────────────┐
│ UserRepository  │
└────┬────────────┘
     │
     │ User trouvé
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ 3. Vérifier compte non verrouillé
     │    user.isAccountNonLocked()
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ 4. Vérifier mot de passe
     │    passwordEncoder.matches()
     │
     ▼
┌─────────────────┐
│ Argon2Password  │
│    Encoder      │
│   matches()      │
└────┬────────────┘
     │
     │ Vérification Argon2
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ 5. Vérifier MFA activé?
     │    mfaService.isMfaEnabled()
     │
     │    Si NON → Continuer
     │    Si OUI → Retourner mfaToken
     │
     ▼
┌─────────────────┐
│  UserService    │
│handleSuccessful │
│    Login()      │
└────┬────────────┘
     │
     │ 1. resetFailedAttempts()
     │ 2. setLastLoginIp()
     │ 3. setLastLoginDevice()
     │
     ▼
┌─────────────────┐
│ SecurityAudit   │
│   Service       │
│ logLoginSuccess()│
└────┬────────────┘
     │
     │ INSERT INTO security_audit_logs
     │
     ▼
┌─────────────────┐
│   JwtUtil       │
│ generateToken() │
└────┬────────────┘
     │
     │ Génère JWT avec:
     │ - username
     │ - roles
     │ - tokenVersion
     │
     ▼
┌─────────────────┐
│RefreshToken     │
│   Service       │
│createRefreshToken│
└────┬────────────┘
     │
     │ 1. Génère UUID
     │ 2. Calcule expiry_date
     │ 3. Sauvegarde en base
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ Retourne:
     │ { token, refreshToken }
     │
     ▼
┌─────────┐
│ Client   │
│ 200 OK   │
│ { token, refreshToken }
└─────────┘
```

---

### Flux 3: Connexion avec MFA

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     │ POST /auth/login
     │ { username, password }
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ ... (vérifications username, password) ...
     │
     │ Vérifier MFA activé?
     │ mfaService.isMfaEnabled() → TRUE
     │
     ▼
┌─────────────────┐
│ MfaTokenService │
│generateMfaToken()│
└────┬────────────┘
     │
     │ Génère token temporaire (5 min)
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ Retourne:
     │ { mfaRequired: true, mfaToken: "..." }
     │
     ▼
┌─────────┐
│ Client   │
│ 200 OK   │
│ { mfaRequired: true, mfaToken }
└────┬────┘
     │
     │ Utilisateur entre code TOTP
     │
     │ POST /auth/mfa/verify
     │ { mfaToken, code: "123456" }
     │
     ▼
┌─────────────────┐
│ AuthController  │
│  verifyMfa()     │
└────┬────────────┘
     │
     │ 1. Valider mfaToken
     │
     ▼
┌─────────────────┐
│ MfaTokenService │
│validateMfaToken()│
└────┬────────────┘
     │
     │ Token valide et non expiré?
     │
     ▼
┌─────────────────┐
│  MfaService     │
│  verifyCode()    │
└────┬────────────┘
     │
     │ Vérifie code TOTP avec secret
     │
     ▼
┌─────────────────┐
│ AuthController  │
│  verifyMfa()     │
└────┬────────────┘
     │
     │ Code valide?
     │
     │ Si OUI:
     │ 1. Génère access token
     │ 2. Génère refresh token
     │ 3. Supprime mfaToken
     │ 4. Log MFA_VERIFICATION_SUCCESS
     │
     ▼
┌─────────┐
│ Client   │
│ 200 OK   │
│ { token, refreshToken }
└─────────┘
```

---

### Flux 4: Renouvellement de Token (Refresh)

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     │ POST /auth/refresh
     │ { refreshToken: "uuid..." }
     │
     ▼
┌─────────────────┐
│ AuthController  │
│ refreshToken()   │
└────┬────────────┘
     │
     │ 1. Valider refreshToken
     │
     ▼
┌─────────────────┐
│RefreshToken     │
│   Service       │
│validateRefresh   │
│    Token()       │
└────┬────────────┘
     │
     │ Vérifications:
     │ - Token existe?
     │ - Token non expiré?
     │ - Token non révoqué?
     │ - tokenVersion == user.tokenVersion?
     │
     ▼
┌─────────────────┐
│RefreshToken     │
│  Repository     │
│  findByToken()  │
└────┬────────────┘
     │
     │ SELECT * FROM refresh_tokens WHERE token = ?
     │
     ▼
┌─────────────────┐
│RefreshToken     │
│   Service       │
└────┬────────────┘
     │
     │ Token valide
     │
     ▼
┌─────────────────┐
│   JwtUtil       │
│ generateToken() │
└────┬────────────┘
     │
     │ Génère nouveau access token
     │
     ▼
┌─────────────────┐
│ SecurityAudit   │
│   Service       │
│ logEvent(TOKEN_ │
│    REFRESH)      │
└────┬────────────┘
     │
     │
     ▼
┌─────────────────┐
│ AuthController  │
│ refreshToken()   │
└────┬────────────┘
     │
     │ Retourne:
     │ { token: "new access token", refreshToken: "same" }
     │
     ▼
┌─────────┐
│ Client   │
│ 200 OK   │
│ { token, refreshToken }
└─────────┘
```

---

## 🔒 Flux de Sécurité

### Flux 1: Protection Brute-Force

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     │ POST /auth/login
     │ { username, password: "wrong" }
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ Vérifier mot de passe
     │ passwordEncoder.matches() → FALSE
     │
     ▼
┌─────────────────┐
│  UserService    │
│handleFailedLogin│
└────┬────────────┘
     │
     │ 1. user.incrementFailedAttempts()
     │
     ▼
┌─────────────────┐
│   User Entity   │
│incrementFailed   │
│  Attempts()     │
└────┬────────────┘
     │
     │ failedLoginAttempts++
     │
     │ Si failedLoginAttempts >= 5:
     │   accountLocked = true
     │   lockTime = now()
     │
     ▼
┌─────────────────┐
│ UserRepository  │
│    save()       │
└────┬────────────┘
     │
     │ UPDATE users SET failed_login_attempts = ?, account_locked = ?
     │
     ▼
┌─────────────────┐
│ SecurityAudit   │
│   Service       │
│ logLoginFailed()│
└────┬────────────┘
     │
     │ INSERT INTO security_audit_logs
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ Retourne:
     │ { error, remainingAttempts: 4 }
     │
     ▼
┌─────────┐
│ Client   │
│ 401      │
│ { error, remainingAttempts }
└─────────┘
```

### Flux 2: Auto-Déverrouillage

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     │ POST /auth/login
     │ { username, password }
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ Vérifier compte verrouillé
     │ user.isAccountNonLocked()
     │
     ▼
┌─────────────────┐
│   User Entity   │
│isAccountNonLocked│
└────┬────────────┘
     │
     │ Vérifier:
     │ if (accountLocked) {
     │   if (lockTime + 15 minutes < now) {
     │     // Auto-déverrouillage
     │     accountLocked = false
     │     failedLoginAttempts = 0
     │     lockTime = null
     │     return true
     │   }
     │   return false
     │ }
     │ return true
     │
     ▼
┌─────────────────┐
│ AuthController  │
│   login()       │
└────┬────────────┘
     │
     │ Si déverrouillé → Continuer avec login
     │ Si toujours verrouillé → 423 Locked
     │
     ▼
```

---

## 🔗 Interactions entre Composants

### Diagramme de Dépendances

```
┌─────────────────┐
│ AuthController  │
└────────┬────────┘
         │
         │ dépend de
         │
    ┌────┴─────────────────────────────────────┐
    │                                          │
    ▼                                          ▼
┌──────────────┐                      ┌──────────────┐
│ UserService  │                      │RefreshToken  │
└──────┬───────┘                      │   Service    │
       │                              └──────┬───────┘
       │ dépend de                           │ dépend de
       │                                     │
    ┌──┴──────────────┐                  ┌───┴──────────────┐
    │                 │                  │                 │
    ▼                 ▼                  ▼                 ▼
┌──────────┐   ┌──────────┐      ┌──────────┐    ┌──────────┐
│UserRepo  │   │Security  │      │Refresh   │    │Security  │
│          │   │Audit     │      │TokenRepo │    │Audit     │
│          │   │Service   │      │          │    │Service   │
└────┬─────┘   └────┬─────┘      └────┬─────┘    └────┬─────┘
     │              │                  │              │
     │              │                  │              │
     └──────────────┴──────────────────┴──────────────┘
                    │
                    │ dépend de
                    │
              ┌─────┴─────┐
              │           │
              ▼           ▼
      ┌──────────┐  ┌──────────┐
      │Security   │  │Security  │
      │AuditLog   │  │AuditLog  │
      │Repository │  │Repository │
      └──────────┘  └──────────┘
```

### Injection de Dépendances

**Exemple dans AuthController:**
```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;
    private final MfaTokenService mfaTokenService;
    
    // Injection via constructeur (recommandé)
    public AuthController(
        UserService userService,
        JwtUtil jwtUtil,
        PasswordEncoder passwordEncoder,
        SecurityAuditService auditService,
        RefreshTokenService refreshTokenService,
        MfaService mfaService,
        MfaTokenService mfaTokenService
    ) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
        this.mfaService = mfaService;
        this.mfaTokenService = mfaTokenService;
    }
}
```

---

## 📦 Structure des Packages

```
com.example.auth_service
│
├── AuthServiceApplication.java       # Point d'entrée Spring Boot
│
├── controller/                        # 📱 PRESENTATION LAYER
│   └── AuthController.java            # Contrôleur REST principal
│
├── service/                           # 💼 BUSINESS LAYER
│   ├── UserService.java               # Service utilisateurs
│   ├── RefreshTokenService.java        # Service refresh tokens
│   ├── SecurityAuditService.java       # Service audit logging
│   ├── MfaService.java                 # Service MFA
│   └── MfaTokenService.java            # Service tokens MFA temporaires
│
├── repository/                        # 💾 DATA ACCESS LAYER
│   ├── UserRepository.java             # Repository utilisateurs
│   ├── RefreshTokenRepository.java     # Repository refresh tokens
│   ├── SecurityAuditLogRepository.java # Repository audit logs
│   └── RoleRepository.java             # Repository rôles
│
├── model/                             # 🗄️ ENTITIES
│   ├── User.java                       # Entité utilisateur
│   ├── RefreshToken.java               # Entité refresh token
│   ├── SecurityAuditLog.java            # Entité audit log
│   └── Role.java                        # Entité rôle
│
├── dto/                               # 📋 DATA TRANSFER OBJECTS
│   ├── AuthRequest.java                # DTO requête login
│   ├── AuthResponse.java               # DTO réponse login
│   ├── RegisterRequest.java            # DTO requête inscription
│   ├── RefreshTokenRequest.java        # DTO requête refresh
│   ├── MfaSetupResponse.java           # DTO réponse setup MFA
│   ├── MfaVerifyRequest.java           # DTO requête vérification MFA
│   ├── MfaLoginResponse.java           # DTO réponse login MFA
│   └── MfaEnableRequest.java           # DTO requête activation MFA
│
└── security/                          # 🔐 SECURITY LAYER
    ├── SecurityConfig.java              # Configuration Spring Security
    ├── JwtUtil.java                     # Utilitaire JWT
    └── Argon2PasswordEncoder.java       # Encodeur Argon2
```

---

## 🛠️ Dépendances et Technologies

### Technologies Principales

| Technologie | Version | Rôle |
|-------------|---------|------|
| **Spring Boot** | 3.5.7 | Framework principal |
| **Java** | 17 | Langage de programmation |
| **PostgreSQL** | Latest | Base de données |
| **Spring Security** | 6.x | Framework de sécurité |
| **JWT (jjwt)** | 0.12.x | Tokens JWT |
| **Argon2** | argon2-jvm | Hashage de mots de passe |
| **TOTP** | totp | Authentification à deux facteurs |
| **Hibernate/JPA** | 6.x | ORM |
| **Lombok** | Latest | Réduction de boilerplate |

### Dépendances Maven Principales

```xml
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- JPA / Hibernate -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
    </dependency>
    
    <!-- Argon2 -->
    <dependency>
        <groupId>de.mkammerer</groupId>
        <artifactId>argon2-jvm</artifactId>
    </dependency>
    
    <!-- TOTP -->
    <dependency>
        <groupId>dev.samstevens.totp</groupId>
        <artifactId>totp</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

---

## 📊 Diagrammes de Séquence

### Séquence 1: Inscription Complète

```
Client          AuthController    UserService    UserRepository    Argon2Encoder    SecurityAudit    PostgreSQL
  │                    │                │              │                  │                 │              │
  │ POST /register     │                │              │                  │                 │              │
  ├───────────────────>│                │              │                  │                 │              │
  │                    │                │              │                  │                 │              │
  │                    │ register()    │              │                  │                 │              │
  │                    ├───────────────>│              │                  │                 │              │
  │                    │                │              │                  │                 │              │
  │                    │                │ existsByUsername()              │                 │              │
  │                    │                ├─────────────>│                 │                 │              │
  │                    │                │              │                 │                 │              │
  │                    │                │              │ SELECT ...       │                 │              │
  │                    │                │              ├─────────────────>│                 │              │
  │                    │                │              │                 │                 │              │
  │                    │                │<─────────────┤                 │                 │              │
  │                    │                │ false        │                 │                 │              │
  │                    │                │              │                 │                 │              │
  │                    │                │ encode(password)               │                 │              │
  │                    │                ├─────────────────────────────────>│                 │              │
  │                    │                │                │                 │                 │              │
  │                    │                │<─────────────────────────────────┤                 │              │
  │                    │                │ hash          │                 │                 │              │
  │                    │                │              │                 │                 │              │
  │                    │                │ save(user)    │                 │                 │              │
  │                    │                ├───────────────>│                 │                 │              │
  │                    │                │                │                 │                 │              │
  │                    │                │                │ INSERT INTO users              │              │
  │                    │                │                ├───────────────────────────────────>│              │
  │                    │                │                │                 │                 │              │
  │                    │                │                │<──────────────────────────────────┤              │
  │                    │                │<───────────────┤                 │                 │              │
  │                    │                │ User           │                 │                 │              │
  │                    │                │              │                 │                 │              │
  │                    │                │ logRegistration()                │                 │              │
  │                    │                ├────────────────────────────────────────────────────>│              │
  │                    │                │                │                 │                 │              │
  │                    │                │                │                 │                 │ INSERT log   │
  │                    │                │                │                 │                 ├─────────────>│
  │                    │                │                │                 │                 │              │
  │                    │                │<────────────────────────────────────────────────────┤              │
  │                    │<───────────────┤                │                 │                 │              │
  │                    │ User           │                │                 │                 │              │
  │                    │                │                │                 │                 │              │
  │<───────────────────┤                │                │                 │                 │              │
  │ 200 OK             │                │                │                 │                 │              │
  │ { message, username }               │                │                 │                 │              │
```

---

## 🎯 Résumé de l'Architecture

### Points Clés

1. **Séparation Claire des Responsabilités**
   - Chaque couche a un rôle bien défini
   - Pas de mélange entre logique métier et accès aux données

2. **Inversion de Dépendances**
   - Les couches supérieures dépendent d'abstractions
   - Injection de dépendances via Spring

3. **Scalabilité**
   - Architecture modulaire
   - Facile d'ajouter de nouvelles fonctionnalités

4. **Sécurité Transversale**
   - Composants de sécurité utilisés par toutes les couches
   - Protection à tous les niveaux

5. **Traçabilité Complète**
   - Tous les événements sont loggés
   - Audit trail complet

### Avantages de cette Architecture

✅ **Maintenabilité:** Code organisé et facile à maintenir
✅ **Testabilité:** Chaque couche peut être testée indépendamment
✅ **Extensibilité:** Facile d'ajouter de nouvelles fonctionnalités
✅ **Sécurité:** Protection à tous les niveaux
✅ **Performance:** Optimisation possible à chaque couche

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
