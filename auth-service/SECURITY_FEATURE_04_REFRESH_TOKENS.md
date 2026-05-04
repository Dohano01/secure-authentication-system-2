# 🔄 Fonctionnalité de Sécurité #4: Refresh Tokens

## 📚 Table des Matières

1. [Concept et Théorie](#concept-et-théorie)
2. [Pourquoi Refresh Tokens?](#pourquoi-refresh-tokens)
3. [Rôle dans l'Architecture](#rôle-dans-larchitecture)
4. [Implémentation Spring Boot](#implémentation-spring-boot)
5. [Endpoints et Flux](#endpoints-et-flux)
6. [Contraintes et Limitations](#contraintes-et-limitations)
7. [Gestion des Sessions](#gestion-des-sessions)

---

## 🎓 Concept et Théorie

### Qu'est-ce qu'un Refresh Token?

Un **refresh token** est un token de longue durée utilisé pour obtenir de nouveaux **access tokens** (JWT) sans avoir à se ré-authentifier avec username/password.

**Caractéristiques:**
- **Longue durée:** 30 jours (vs 1 heure pour access token)
- **Stocké en base:** Permet la révocation
- **Unique:** UUID généré aléatoirement
- **Trackable:** IP, device, user agent enregistrés

### Pourquoi Deux Types de Tokens?

**Access Token (JWT):**
- ✅ Court (1 heure)
- ✅ Contient les permissions
- ✅ Stateless (pas de DB)
- ❌ Expire rapidement

**Refresh Token:**
- ✅ Long (30 jours)
- ✅ Révocable (stocké en DB)
- ✅ Permet de renouveler l'access token
- ❌ Ne contient pas de permissions

### Flux d'Authentification avec Refresh Tokens

```
1. Login (username/password)
   ↓
2. Serveur génère:
   - Access Token (JWT, 1 heure)
   - Refresh Token (UUID, 30 jours)
   ↓
3. Client stocke les deux tokens
   ↓
4. Client utilise Access Token pour les requêtes
   ↓
5. Access Token expire (1 heure)
   ↓
6. Client utilise Refresh Token pour obtenir nouveau Access Token
   ↓
7. Serveur valide Refresh Token et génère nouveau Access Token
   ↓
8. Répéter jusqu'à expiration du Refresh Token (30 jours)
```

---

## 🤔 Pourquoi Refresh Tokens?

### Problème Sans Refresh Tokens

**Scénario 1: Access Token Court (Sécurisé mais Inconfortable)**
```
Access Token: 15 minutes
→ Utilisateur doit se reconnecter toutes les 15 minutes ❌
```

**Scénario 2: Access Token Long (Inconfortable mais Insecure)**
```
Access Token: 30 jours
→ Si volé, attaquant a accès pendant 30 jours ❌
```

### Solution: Refresh Tokens

**Access Token Court + Refresh Token Long:**
```
Access Token: 1 heure (sécurisé)
Refresh Token: 30 jours (confortable)
→ Meilleur équilibre sécurité/confort ✅
```

### Avantages

1. **Sécurité:**
   - Access tokens expirent rapidement
   - Refresh tokens peuvent être révoqués
   - Tracking de toutes les sessions

2. **Confort Utilisateur:**
   - Pas besoin de se reconnecter fréquemment
   - Renouvellement automatique des tokens

3. **Contrôle:**
   - Logout de tous les appareils possible
   - Invalidation globale des tokens
   - Monitoring des sessions actives

---

## 🏗️ Rôle dans l'Architecture

### Position dans l'Architecture

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│         (AuthController)                 │
│  POST /auth/login → Génère refresh token │
│  POST /auth/refresh → Valide refresh    │
│  POST /auth/logout → Révoque token       │
│  POST /auth/logout-all → Révoque tous    │
│  GET /auth/sessions → Liste sessions     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         BUSINESS LAYER                   │
│         (RefreshTokenService)             │
│  - createRefreshToken()                   │
│  - validateRefreshToken()                 │
│  - revokeToken()                          │
│  - revokeAllUserTokens()                  │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATA LAYER                       │
│         (RefreshTokenRepository)         │
│  - findByToken()                          │
│  - findByUser()                           │
│  - revokeAllUserTokens()                  │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATABASE                         │
│         (refresh_tokens table)           │
│  - token (UUID)                          │
│  - user_id                               │
│  - expiry_date                           │
│  - revoked                               │
│  - token_version                         │
└─────────────────────────────────────────┘
```

### Flux de Refresh Token

**1. Création (Login):**
```
User → POST /auth/login { username, password }
  ↓
AuthController vérifie credentials
  ↓
RefreshTokenService.createRefreshToken(user, ip, userAgent, device)
  ↓
Génère UUID unique
  ↓
Sauvegarde en base avec:
  - token: UUID
  - user_id: user.id
  - expiry_date: now + 30 jours
  - token_version: user.tokenVersion
  - revoked: false
  ↓
Retourne refresh token au client
```

**2. Validation (Refresh):**
```
Client → POST /auth/refresh { refreshToken }
  ↓
RefreshTokenService.validateRefreshToken(token)
  ↓
Vérifie:
  - Token existe en base?
  - Token non expiré?
  - Token non révoqué?
  - token_version == user.tokenVersion?
  ↓
Si valide → Génère nouveau access token
  ↓
Retourne nouveau access token
```

**3. Révocation (Logout):**
```
Client → POST /auth/logout { refreshToken }
  ↓
RefreshTokenService.revokeToken(token)
  ↓
Met revoked = true en base
  ↓
Token ne peut plus être utilisé
```

---

## 💻 Implémentation Spring Boot

### 1. Modèle de Données

**Fichier:** `RefreshToken.java`

```java
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 500)
    private String token;  // UUID généré
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;  // 30 jours après création
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;  // Date de création
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // IP de création
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;  // User agent de création
    
    @Column(name = "device_type", length = 50)
    private String deviceType;  // Desktop/Mobile/Tablet
    
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion;  // Version pour invalidation globale
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean revoked = false;  // Révocable
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    /**
     * Vérifie si le token est expiré
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
    
    /**
     * Vérifie si le token est valide (non révoqué et non expiré)
     */
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
```

### 2. Repository

**Fichier:** `RefreshTokenRepository.java`

```java
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    /**
     * Trouve un token par sa valeur
     */
    Optional<RefreshToken> findByToken(String token);
    
    /**
     * Trouve tous les tokens actifs d'un utilisateur
     */
    List<RefreshToken> findByUserAndRevokedFalse(User user);
    
    /**
     * Trouve tous les tokens d'un utilisateur (actifs et révoqués)
     */
    List<RefreshToken> findByUser(User user);
    
    /**
     * Supprime les tokens expirés (nettoyage)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    void deleteExpiredTokens(LocalDateTime now);
    
    /**
     * Révoque tous les tokens d'un utilisateur (logout all)
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
    void revokeAllUserTokens(User user);
    
    /**
     * Révoque les tokens avec une ancienne version (invalidation globale)
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.tokenVersion < :tokenVersion")
    void revokeTokensByVersion(User user, Integer tokenVersion);
}
```

### 3. Service Métier

**Fichier:** `RefreshTokenService.java`

```java
@Service
@Slf4j
public class RefreshTokenService {
    
    private final RefreshTokenRepository refreshTokenRepository;
    
    @Value("${jwt.refresh-expiration-days:30}")
    private int refreshExpirationDays;  // 30 jours par défaut
    
    /**
     * Crée un nouveau refresh token pour un utilisateur
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, String ipAddress, 
                                           String userAgent, String deviceType) {
        // Génère un UUID unique
        String token = UUID.randomUUID().toString();
        
        // Calcule la date d'expiration (30 jours)
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(refreshExpirationDays);
        
        // Crée le refresh token
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(deviceType)
                .tokenVersion(user.getTokenVersion())  // Version actuelle de l'utilisateur
                .revoked(false)
                .build();
        
        return refreshTokenRepository.save(refreshToken);
    }
    
    /**
     * Valide un refresh token et le retourne si valide
     */
    @Transactional
    public Optional<RefreshToken> validateRefreshToken(String token) {
        // Trouve le token en base
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
        
        if (refreshTokenOpt.isEmpty()) {
            return Optional.empty();  // Token n'existe pas
        }
        
        RefreshToken refreshToken = refreshTokenOpt.get();
        
        // Vérifie si le token est valide (non révoqué et non expiré)
        if (!refreshToken.isValid()) {
            return Optional.empty();  // Token expiré ou révoqué
        }
        
        // Vérifie si la version du token correspond à celle de l'utilisateur
        User user = refreshToken.getUser();
        if (!refreshToken.getTokenVersion().equals(user.getTokenVersion())) {
            // Version mismatch - token a été invalidé globalement
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            return Optional.empty();
        }
        
        return Optional.of(refreshToken);
    }
    
    /**
     * Révoque un refresh token spécifique
     */
    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            log.info("Refresh token revoked for user: {}", refreshToken.getUser().getUsername());
        });
    }
    
    /**
     * Révoque tous les tokens d'un utilisateur (logout de tous les appareils)
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("All refresh tokens revoked for user: {}", user.getUsername());
    }
    
    /**
     * Révoque les tokens avec une ancienne version (après incrémentation de tokenVersion)
     */
    @Transactional
    public void revokeTokensByVersion(User user, Integer oldTokenVersion) {
        refreshTokenRepository.revokeTokensByVersion(user, oldTokenVersion);
    }
    
    /**
     * Récupère toutes les sessions actives d'un utilisateur
     */
    public List<RefreshToken> getUserActiveSessions(User user) {
        return refreshTokenRepository.findByUserAndRevokedFalse(user);
    }
}
```

### 4. Configuration

**Fichier:** `application.properties`

```properties
# Refresh Token Configuration
jwt.refresh-expiration-days=30  # Durée de vie en jours
```

---

## 🌐 Endpoints et Flux

### 1. POST /auth/login - Génération de Refresh Token

**Description:** Génère un refresh token lors de la connexion

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Traitement Interne:**
1. Authentification réussie
2. `RefreshTokenService.createRefreshToken()` appelé
3. UUID généré: `550e8400-e29b-41d4-a716-446655440000`
4. Sauvegarde en base avec:
   - `expiry_date`: 30 jours
   - `token_version`: user.tokenVersion
   - `ip_address`: IP du client
   - `device_type`: Desktop/Mobile/Tablet
5. Retour au client

**État en Base:**
```sql
SELECT token, user_id, expiry_date, revoked, token_version 
FROM refresh_tokens 
WHERE token = '550e8400-e29b-41d4-a716-446655440000';
```
```
token: 550e8400-e29b-41d4-a716-446655440000
user_id: 1
expiry_date: 2026-02-14 12:30:00
revoked: false
token_version: 0
```

---

### 2. POST /auth/refresh - Renouvellement d'Access Token

**Description:** Génère un nouveau access token en utilisant un refresh token

**Request:**
```http
POST http://localhost:8081/auth/refresh
Content-Type: application/json

{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.NewAccessToken...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Traitement Interne:**
1. `RefreshTokenService.validateRefreshToken()` appelé
2. Vérifications:
   - Token existe? ✅
   - Token non expiré? ✅
   - Token non révoqué? ✅
   - `token_version == user.tokenVersion`? ✅
3. Si valide → Génère nouveau access token
4. Retourne nouveau access token (même refresh token)

**Response (401 Unauthorized) - Token Invalide:**
```json
{
  "error": "Invalid refresh token",
  "message": "Refresh token is invalid, expired, or revoked"
}
```

**Scénarios d'Échec:**
- Token n'existe pas en base
- Token expiré (expiry_date < now)
- Token révoqué (revoked = true)
- Version mismatch (token_version < user.tokenVersion)

---

### 3. POST /auth/logout - Déconnexion (Révoque un Token)

**Description:** Révoque un refresh token spécifique (logout d'un appareil)

**Request:**
```http
POST http://localhost:8081/auth/logout
Content-Type: application/json

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

**Traitement Interne:**
1. `RefreshTokenService.revokeToken()` appelé
2. Met `revoked = true` en base
3. Token ne peut plus être utilisé pour refresh
4. Log `LOGOUT` dans audit logs

**État en Base Après Logout:**
```sql
SELECT revoked FROM refresh_tokens WHERE token = '550e8400-e29b-41d4-a716-446655440000';
```
```
revoked: true
```

**Tentative de Refresh Après Logout:**
```http
POST /auth/refresh
{ "refreshToken": "550e8400-e29b-41d4-a716-446655440000" }
```
→ **401 Unauthorized** (token révoqué)

---

### 4. POST /auth/logout-all - Déconnexion de Tous les Appareils

**Description:** Révoque tous les refresh tokens d'un utilisateur

**Request:**
```http
POST http://localhost:8081/auth/logout-all
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Response (200 OK):**
```json
{
  "message": "Logged out from all devices successfully",
  "username": "admin"
}
```

**Traitement Interne:**
1. Extraction du username depuis l'access token
2. Récupération de l'utilisateur
3. `RefreshTokenService.revokeAllUserTokens(user)` appelé
4. Tous les tokens de l'utilisateur sont révoqués (`revoked = true`)
5. Log `LOGOUT_ALL` dans audit logs

**État en Base Après Logout-All:**
```sql
SELECT token, revoked FROM refresh_tokens WHERE user_id = 1;
```
```
token: 550e8400-e29b-41d4-a716-446655440000, revoked: true
token: 660e8400-e29b-41d4-a716-446655440001, revoked: true
token: 770e8400-e29b-41d4-a716-446655440002, revoked: true
```

**⚠️ Important:** Tous les appareils sont déconnectés, même ceux qui n'ont pas initié le logout.

---

### 5. GET /auth/sessions - Liste des Sessions Actives

**Description:** Récupère toutes les sessions actives d'un utilisateur

**Request:**
```http
GET http://localhost:8081/auth/sessions
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Response (200 OK):**
```json
[
  {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "deviceType": "Desktop",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "createdAt": "2026-01-14T12:30:00",
    "expiryDate": "2026-02-14T12:30:00",
    "revoked": false
  },
  {
    "token": "660e8400-e29b-41d4-a716-446655440001",
    "deviceType": "Mobile",
    "ipAddress": "192.168.1.101",
    "userAgent": "Mozilla/5.0 Mobile...",
    "createdAt": "2026-01-14T13:00:00",
    "expiryDate": "2026-02-14T13:00:00",
    "revoked": false
  }
]
```

**Traitement Interne:**
1. Extraction du username depuis l'access token
2. Récupération de l'utilisateur
3. `RefreshTokenService.getUserActiveSessions(user)` appelé
4. Retourne tous les tokens non révoqués

**Utilisation:**
- Voir tous les appareils connectés
- Détecter des sessions suspectes
- Déclencher un logout-all si nécessaire

---

## 🚫 Contraintes et Limitations

### Contraintes Configurées

1. **Durée de Vie:**
   - **Valeur:** 30 jours
   - **Code:** `jwt.refresh-expiration-days=30`
   - **Modifiable:** Oui, dans `application.properties`

2. **Format:**
   - **Type:** UUID v4
   - **Longueur:** 36 caractères (avec tirets)
   - **Exemple:** `550e8400-e29b-41d4-a716-446655440000`

3. **Stockage:**
   - **Table:** `refresh_tokens`
   - **Contrainte:** `token` est unique
   - **Index:** Sur `token` et `user_id`

### Limitations

1. **Pas de Rotation Automatique:**
   - Le même refresh token est retourné après refresh
   - Pour rotation, générer un nouveau token à chaque refresh

2. **Nettoyage Manuel:**
   - Les tokens expirés restent en base
   - Nécessite un job de nettoyage périodique

3. **Pas de Limite de Sessions:**
   - Un utilisateur peut avoir un nombre illimité de sessions
   - Pour limiter, ajouter une vérification avant création

### Améliorations Possibles

1. **Rotation des Refresh Tokens:**
   - Générer un nouveau refresh token à chaque refresh
   - Révoquer l'ancien token
   - Meilleure sécurité

2. **Limite de Sessions:**
   - Maximum X sessions par utilisateur
   - Révoquer la plus ancienne si limite atteinte

3. **Nettoyage Automatique:**
   - Job Spring qui supprime les tokens expirés
   - Exécution quotidienne

---

## 🔍 Gestion des Sessions

### Invalidation Globale (Token Versioning)

**Scénario:** Utilisateur change son mot de passe ou suspecte un compromis

**Code:**
```java
// Dans UserService
public void invalidateAllTokens(User user) {
    // Incrémente la version du token
    user.incrementTokenVersion();
    userRepository.save(user);
    
    // Révoque tous les tokens avec l'ancienne version
    refreshTokenService.revokeTokensByVersion(user, user.getTokenVersion() - 1);
}
```

**Résultat:**
- Tous les refresh tokens existants deviennent invalides
- Nouveaux tokens créés avec la nouvelle version
- Utilisateurs doivent se reconnecter

### Monitoring des Sessions

**Requête SQL pour Voir Toutes les Sessions:**
```sql
SELECT 
    rt.token,
    u.username,
    rt.device_type,
    rt.ip_address,
    rt.created_at,
    rt.expiry_date,
    rt.revoked
FROM refresh_tokens rt
JOIN users u ON rt.user_id = u.id
WHERE rt.revoked = false
ORDER BY rt.created_at DESC;
```

**Requête pour Sessions Suspectes:**
```sql
-- Sessions depuis IPs différentes
SELECT 
    u.username,
    COUNT(DISTINCT rt.ip_address) as unique_ips,
    COUNT(*) as total_sessions
FROM refresh_tokens rt
JOIN users u ON rt.user_id = u.id
WHERE rt.revoked = false
GROUP BY u.username
HAVING COUNT(DISTINCT rt.ip_address) > 3;  -- Plus de 3 IPs différentes
```

---

## 🎯 Résumé

### Concept
Refresh tokens sont des tokens de longue durée stockés en base qui permettent de renouveler les access tokens sans ré-authentification.

### Rôle dans l'Architecture
- **Modèle:** `RefreshToken` - Stocke les tokens en base
- **Service:** `RefreshTokenService` - Gère le cycle de vie
- **Contrôleur:** `AuthController` - Endpoints de gestion
- **Base de données:** Table `refresh_tokens`

### Implémentation
- **Format:** UUID v4
- **Durée:** 30 jours
- **Stockage:** Base de données PostgreSQL
- **Révocable:** Oui (champ `revoked`)

### Endpoints
- `POST /auth/login` - Génère refresh token
- `POST /auth/refresh` - Renouvelle access token
- `POST /auth/logout` - Révoque un token
- `POST /auth/logout-all` - Révoque tous les tokens
- `GET /auth/sessions` - Liste les sessions actives

### Sécurité
- ✅ Révocable individuellement ou en masse
- ✅ Tracking de toutes les sessions
- ✅ Versioning pour invalidation globale
- ✅ Expiration automatique

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
