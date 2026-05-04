# 🔄 Fonctionnalité de Sécurité #3: Système de Tokens JWT

## 📚 Table des Matières

1. [Concept et Théorie](#concept-et-théorie)
2. [Pourquoi JWT?](#pourquoi-jwt)
3. [Rôle dans l'Architecture](#rôle-dans-larchitecture)
4. [Implémentation Spring Boot](#implémentation-spring-boot)
5. [Endpoints Utilisant JWT](#endpoints-utilisant-jwt)
6. [Contraintes et Limitations](#contraintes-et-limitations)
7. [Structure et Format](#structure-et-format)

---

## 🎓 Concept et Théorie

### Qu'est-ce qu'un JWT (JSON Web Token)?

Un **JWT** est un standard ouvert (RFC 7519) qui définit une méthode compacte et autonome pour transmettre de manière sécurisée des informations entre parties sous forme d'objet JSON.

**Caractéristiques:**
- **Stateless:** Pas besoin de stocker l'état sur le serveur
- **Portable:** Peut être utilisé sur n'importe quelle plateforme
- **Signé:** Garantit l'intégrité des données
- **Compact:** Facile à transmettre via URL, POST, ou header HTTP

### Structure d'un JWT

Un JWT est composé de **3 parties** séparées par des points (`.`):

```
header.payload.signature
```

**Exemple:**
```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0sImlhdCI6MTYzOTg3NjU0MywiZXhwIjoxNjM5ODgwMTQzfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
│─────────────────││──────────────────────────────────────────────────────────────││──────────────────────────────────────────│
      Header                    Payload (Claims)                                              Signature
```

### Les 3 Parties du JWT

#### 1. Header (En-tête)
```json
{
  "alg": "HS512",
  "typ": "JWT"
}
```
- **alg:** Algorithme de signature (HS512 = HMAC SHA-512)
- **typ:** Type de token (toujours "JWT")

#### 2. Payload (Charge Utile / Claims)
```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "tokenVersion": 0,
  "iat": 1639876543,
  "exp": 1639880143
}
```
- **sub:** Subject (username)
- **roles:** Liste des rôles
- **tokenVersion:** Version pour invalidation globale
- **iat:** Issued At (date d'émission)
- **exp:** Expiration (date d'expiration)

#### 3. Signature
```
HMACSHA512(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)
```
- Garantit que le token n'a pas été modifié
- Vérifie l'authenticité du token

---

## 🤔 Pourquoi JWT?

### Avantages par Rapport aux Sessions

| Aspect | Sessions Traditionnelles | JWT |
|--------|-------------------------|-----|
| **Stockage** | Serveur (mémoire/DB) | Client (stateless) |
| **Scalabilité** | Nécessite sticky sessions | Horizontal scaling facile |
| **Performance** | Requête DB à chaque requête | Validation locale |
| **CORS** | Cookies avec credentials | Headers simples |
| **Mobile** | Cookies non supportés | Headers supportés |

### Avantages de Sécurité

1. **Signature Cryptographique**
   - Impossible de falsifier sans le secret
   - Détection automatique de modification

2. **Expiration Automatique**
   - Tokens expirent après un délai défini
   - Réduit la fenêtre d'attaque si volé

3. **Versioning**
   - Invalidation globale possible
   - Logout de tous les appareils

4. **Claims Personnalisés**
   - Rôles, permissions, métadonnées
   - Pas besoin de requête DB pour autorisation

---

## 🏗️ Rôle dans l'Architecture

### Position dans l'Architecture

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│         (AuthController)                 │
│  POST /auth/login → Génère JWT           │
│  GET /auth/validate → Valide JWT         │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         BUSINESS LAYER                   │
│         (UserService)                    │
│  - Récupère user et roles                │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│      SECURITY LAYER (Transversale)      │
│      (JwtUtil)                          │
│  - generateToken() → Crée JWT            │
│  - validateTokenAndGetClaims() → Valide │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         CLIENT                           │
│  - Stocke JWT (localStorage/cookie)      │
│  - Envoie dans header Authorization      │
└─────────────────────────────────────────┘
```

### Flux d'Authentification avec JWT

**1. Connexion (Login):**
```
User → POST /auth/login { username, password }
  ↓
AuthController vérifie credentials
  ↓
UserService récupère user + roles
  ↓
JwtUtil.generateToken(username, roles, tokenVersion)
  ↓
JWT créé et retourné au client
  ↓
Client stocke JWT
```

**2. Requête Authentifiée:**
```
Client → GET /api/protected
  Header: Authorization: Bearer <JWT>
  ↓
JwtFilter intercepte la requête
  ↓
JwtUtil.validateTokenAndGetClaims(JWT)
  ↓
Si valide → Extrait username et roles
  ↓
Spring Security autorise la requête
  ↓
Controller traite la requête
```

**3. Validation:**
```
Client → GET /auth/validate
  Header: Authorization: Bearer <JWT>
  ↓
AuthController.validateToken()
  ↓
JwtUtil.validateTokenAndGetClaims(JWT)
  ↓
Retourne les claims (username, roles, etc.)
```

---

## 💻 Implémentation Spring Boot

### Fichier: `JwtUtil.java`

**Localisation:** `com.example.auth_service.security.JwtUtil`

**Code Complet:**
```java
package com.example.auth_service.security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.nio.charset.StandardCharsets;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Génère un token JWT avec username et roles
     * 
     * @param username Nom d'utilisateur (subject)
     * @param roles Liste des rôles de l'utilisateur
     * @return Token JWT signé
     */
    public String generateToken(String username, List<String> roles) {
        return generateToken(username, roles, null);
    }
    
    /**
     * Génère un token JWT avec username, roles et tokenVersion
     * 
     * @param username Nom d'utilisateur (subject)
     * @param roles Liste des rôles
     * @param tokenVersion Version du token (pour invalidation globale)
     * @return Token JWT signé
     */
    public String generateToken(String username, List<String> roles, Integer tokenVersion) {
        // Créer les claims (données dans le payload)
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        
        // Ajouter tokenVersion si fourni (pour invalidation globale)
        if (tokenVersion != null) {
            claims.put("tokenVersion", tokenVersion);
        }

        long now = System.currentTimeMillis();
        
        // Construire le JWT
        return Jwts.builder()
                .setClaims(claims)                    // Claims personnalisés
                .setSubject(username)                 // Subject (username)
                .setIssuedAt(new Date(now))           // Date d'émission
                .setExpiration(new Date(now + expirationMs))  // Date d'expiration
                .signWith(
                    SignatureAlgorithm.HS512,        // Algorithme: HMAC SHA-512
                    secret.getBytes(StandardCharsets.UTF_8)  // Secret pour signature
                )
                .compact();                           // Génère le token final
    }

    /**
     * Valide un token JWT et retourne les claims
     * 
     * @param token Token JWT à valider
     * @return Claims du token (username, roles, etc.)
     * @throws JwtException Si le token est invalide, expiré, ou malformé
     */
    public Claims validateTokenAndGetClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)  // Parse et valide le token
                .getBody();              // Retourne les claims
    }
}
```

### Configuration

**Fichier:** `application.properties`

```properties
# JWT Configuration
jwt.secret=MySuperSecretKeyThatIsAtLeast256BitsLongForHS512AlgorithmSecurity
jwt.expiration-ms=3600000  # 1 heure en millisecondes
```

**Explication:**
- **secret:** Clé secrète pour signer les tokens (minimum 256 bits pour HS512)
- **expiration-ms:** Durée de vie du token (3600000 ms = 1 heure)

### Utilisation dans AuthController

**Fichier:** `AuthController.java`

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final JwtUtil jwtUtil;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, ...) {
        // ... vérification credentials ...
        
        // Récupérer les rôles de l'utilisateur
        var roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());
        
        // Générer le token JWT
        String accessToken = jwtUtil.generateToken(
            user.getUsername(), 
            roles, 
            user.getTokenVersion()  // Version pour invalidation globale
        );
        
        return ResponseEntity.ok(AuthResponse.builder()
                .token(accessToken)  // ✅ JWT retourné au client
                .refreshToken(refreshToken.getToken())
                .build());
    }
    
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String header) {
        try {
            // Extraire le token du header
            if (header == null || !header.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
            }
            String token = header.substring(7);  // Enlever "Bearer "
            
            // Valider le token et récupérer les claims
            var claims = jwtUtil.validateTokenAndGetClaims(token);
            
            // Retourner les claims
            return ResponseEntity.ok(claims);
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Token expired",
                "message", "Token has expired at " + e.getClaims().getExpiration()
            ));
        } catch (JwtException e) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid token",
                "message", e.getMessage()
            ));
        }
    }
}
```

---

## 🌐 Endpoints Utilisant JWT

### 1. POST /auth/login - Génération de JWT

**Description:** Génère un JWT après authentification réussie

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
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0sInRva2VuVmVyc2lvbiI6MCwiaWF0IjoxNjM5ODc2NTQzLCJleHAiOjE2Mzk4ODAxNDN9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Traitement Interne:**
1. Vérification des credentials
2. Récupération des rôles: `["ADMIN"]`
3. Génération JWT avec:
   - `sub`: "admin"
   - `roles`: ["ADMIN"]
   - `tokenVersion`: 0
   - `iat`: 1639876543
   - `exp`: 1639880143 (iat + 1 heure)
4. Signature avec secret HS512
5. Retour du token au client

**Format du Token:**
```
eyJhbGciOiJIUzUxMiJ9                                    ← Header (Base64)
.                                                       ← Séparateur
eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0s...      ← Payload (Base64)
.                                                       ← Séparateur
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c            ← Signature
```

---

### 2. GET /auth/validate - Validation de JWT

**Description:** Valide un JWT et retourne ses claims

**Request:**
```http
GET http://localhost:8081/auth/validate
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0sInRva2VuVmVyc2lvbiI6MCwiaWF0IjoxNjM5ODc2NTQzLCJleHAiOjE2Mzk4ODAxNDN9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

**Response (200 OK) - Token Valide:**
```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "tokenVersion": 0,
  "iat": 1639876543,
  "exp": 1639880143
}
```

**Traitement Interne:**
1. Extraction du token du header `Authorization`
2. Vérification de la signature avec le secret
3. Vérification de l'expiration (`exp > now`)
4. Extraction des claims
5. Retour des claims

**Response (401 Unauthorized) - Token Expiré:**
```json
{
  "error": "Token expired",
  "message": "Token has expired at Mon Dec 20 12:00:43 CET 2021"
}
```

**Response (401 Unauthorized) - Token Invalide:**
```json
{
  "error": "Invalid token",
  "message": "JWT signature does not match locally computed signature"
}
```

---

### 3. POST /auth/refresh - Génération Nouveau JWT

**Description:** Génère un nouveau JWT en utilisant un refresh token

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
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0sInRva2VuVmVyc2lvbiI6MCwiaWF0IjoxNjM5ODc3MTIzLCJleHAiOjE2Mzk4ODA3MjN9.NewSignatureHere",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Traitement Interne:**
1. Validation du refresh token
2. Récupération de l'utilisateur
3. Génération d'un **nouveau** JWT avec:
   - Même `sub` et `roles`
   - **Nouveau** `iat` et `exp` (1 heure à partir de maintenant)
   - Même `tokenVersion`
4. Retour du nouveau token

**⚠️ Important:** Le nouveau token a une nouvelle expiration, mais les mêmes permissions.

---

## 🚫 Contraintes et Limitations

### Contraintes Configurées

1. **Durée de Vie:**
   - **Valeur:** 1 heure (3600000 ms)
   - **Code:** `jwt.expiration-ms=3600000`
   - **Modifiable:** Oui, dans `application.properties`

2. **Algorithme de Signature:**
   - **Valeur:** HS512 (HMAC SHA-512)
   - **Code:** `SignatureAlgorithm.HS512`
   - **Modifiable:** Oui, dans `JwtUtil.generateToken()`

3. **Secret:**
   - **Longueur minimale:** 256 bits (32 caractères)
   - **Stockage:** `application.properties` (⚠️ En production, utiliser variables d'environnement)
   - **Sécurité:** Ne jamais exposer le secret

### Limitations

1. **Stateless = Pas de Révocation Immédiate:**
   - Un JWT valide reste valide jusqu'à expiration
   - Pour révoquer, utiliser `tokenVersion` (invalidation globale)

2. **Taille du Token:**
   - Plus de claims = token plus long
   - Limite pratique: ~8 KB (limite de certains serveurs)

3. **Pas de Refresh Automatique:**
   - Le client doit gérer l'expiration
   - Utiliser refresh tokens pour renouveler

4. **Secret Partagé:**
   - Le même secret signe et valide tous les tokens
   - Si le secret est compromis, tous les tokens sont compromis

### Améliorations Possibles

1. **Rotation du Secret:**
   - Changer le secret périodiquement
   - Invalider tous les tokens existants

2. **Blacklist de Tokens:**
   - Stocker les tokens révoqués en base
   - Vérifier la blacklist à chaque validation

3. **Tokens Plus Courts:**
   - Utiliser des IDs de session au lieu de tous les claims
   - Stocker les détails en base

---

## 📊 Structure et Format

### Décodage d'un JWT

**Token Exemple:**
```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0sInRva2VuVmVyc2lvbiI6MCwiaWF0IjoxNjM5ODc2NTQzLCJleHAiOjE2Mzk4ODAxNDN9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

**1. Header (Décodé):**
```json
{
  "alg": "HS512",
  "typ": "JWT"
}
```

**2. Payload (Décodé):**
```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "tokenVersion": 0,
  "iat": 1639876543,
  "exp": 1639880143
}
```

**3. Signature:**
```
HMACSHA512(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  "MySuperSecretKey..."
)
```

### Claims Standards (JWT)

| Claim | Nom Complet | Description | Exemple |
|-------|-------------|-------------|---------|
| `sub` | Subject | Username | "admin" |
| `iat` | Issued At | Date d'émission (timestamp) | 1639876543 |
| `exp` | Expiration | Date d'expiration (timestamp) | 1639880143 |

### Claims Personnalisés

| Claim | Description | Exemple |
|-------|-------------|---------|
| `roles` | Liste des rôles | `["ADMIN", "USER"]` |
| `tokenVersion` | Version pour invalidation | `0` |

---

## 🔍 Vérification et Debugging

### Décoder un JWT (Sans Validation)

**Site Web:** https://jwt.io

1. Coller le token
2. Voir le header et payload décodés
3. ⚠️ Ne pas exposer le secret

### Vérifier l'Expiration

```java
Claims claims = jwtUtil.validateTokenAndGetClaims(token);
Date expiration = claims.getExpiration();
Date now = new Date();

if (now.after(expiration)) {
    // Token expiré
} else {
    long remainingMs = expiration.getTime() - now.getTime();
    long remainingMinutes = remainingMs / 60000;
    // Token valide pour encore X minutes
}
```

### Vérifier les Rôles

```java
Claims claims = jwtUtil.validateTokenAndGetClaims(token);
List<String> roles = (List<String>) claims.get("roles");

if (roles.contains("ADMIN")) {
    // Utilisateur est admin
}
```

---

## 🎯 Résumé

### Concept
JWT est un standard pour transmettre de manière sécurisée des informations entre parties sous forme d'objet JSON signé.

### Rôle dans l'Architecture
- **Couche transversale** (Security Layer)
- Utilisé par **AuthController** (génération et validation)
- **Stateless** - Pas de stockage serveur
- **Portable** - Utilisable sur toutes les plateformes

### Implémentation
- **Classe:** `JwtUtil` - Génération et validation
- **Configuration:** HS512, 1 heure d'expiration
- **Claims:** username, roles, tokenVersion, iat, exp

### Endpoints
- `POST /auth/login` - Génère JWT après authentification
- `GET /auth/validate` - Valide un JWT et retourne les claims
- `POST /auth/refresh` - Génère un nouveau JWT

### Sécurité
- ✅ Signature cryptographique (HS512)
- ✅ Expiration automatique
- ✅ Versioning pour invalidation globale
- ✅ Claims personnalisés (rôles)

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
