# 🔐 Fonctionnalité de Sécurité #7: Multi-Factor Authentication (MFA)

## 📚 Table des Matières

1. [Concept et Théorie](#concept-et-théorie)
2. [Pourquoi MFA?](#pourquoi-mfa)
3. [Rôle dans l'Architecture](#rôle-dans-larchitecture)
4. [Implémentation Spring Boot](#implémentation-spring-boot)
5. [Endpoints et Flux](#endpoints-et-flux)
6. [Contraintes et Limitations](#contraintes-et-limitations)
7. [Configuration TOTP](#configuration-totp)

---

## 🎓 Concept et Théorie

### Qu'est-ce que MFA?

**Multi-Factor Authentication (MFA)** est une méthode d'authentification qui nécessite deux facteurs ou plus pour vérifier l'identité d'un utilisateur.

**Les 3 Facteurs d'Authentification:**

1. **Quelque chose que vous SAVEZ** (Knowledge)
   - Mot de passe
   - PIN
   - Réponse à une question secrète

2. **Quelque chose que vous AVEZ** (Possession)
   - Téléphone
   - Carte à puce
   - Token matériel

3. **Quelque chose que vous ÊTES** (Inherence)
   - Empreinte digitale
   - Reconnaissance faciale
   - Iris

### MFA vs 2FA

- **2FA (Two-Factor Authentication):** Exactement 2 facteurs
- **MFA (Multi-Factor Authentication):** 2 facteurs ou plus

Dans ce projet, nous implémentons **2FA** avec:
1. Mot de passe (quelque chose que vous savez)
2. Code TOTP (quelque chose que vous avez - téléphone)

### Qu'est-ce que TOTP?

**TOTP (Time-based One-Time Password)** est un algorithme qui génère des codes à usage unique basés sur le temps.

**Caractéristiques:**
- **Code à 6 chiffres**
- **Valide pendant 30 secondes**
- **Généré localement** (pas besoin de connexion Internet)
- **Standard:** RFC 6238

**Fonctionnement:**
```
Secret (partagé) + Temps actuel (en intervalles de 30s)
  ↓
Algorithme HMAC-SHA1
  ↓
Code à 6 chiffres (change toutes les 30 secondes)
```

**Exemple:**
```
Temps: 12:00:00 → Code: 123456
Temps: 12:00:30 → Code: 789012
Temps: 12:01:00 → Code: 345678
```

---

## 🤔 Pourquoi MFA?

### Statistiques de Sécurité

- **99.9%** des comptes avec MFA sont protégés contre les attaques par mot de passe
- **80%** des violations de données pourraient être évitées avec MFA
- **Google:** MFA bloque 100% des bots automatisés

### Scénarios de Protection

**Sans MFA:**
```
1. Attaquant obtient le mot de passe (phishing, fuite de données)
2. Attaquant se connecte avec le mot de passe
3. ✅ Compte compromis
```

**Avec MFA:**
```
1. Attaquant obtient le mot de passe
2. Attaquant se connecte avec le mot de passe
3. Système demande code TOTP
4. Attaquant n'a pas accès au téléphone
5. ❌ Connexion bloquée
```

### Avantages

1. **Protection contre le Vol de Mot de Passe:**
   - Même si le mot de passe est volé, le compte reste protégé
   - Le code TOTP change toutes les 30 secondes

2. **Résistance au Phishing:**
   - Le code TOTP est généré localement
   - Impossible de réutiliser un code déjà utilisé

3. **Conformité:**
   - Répond aux exigences de sécurité élevées
   - Requis par certaines réglementations (PCI-DSS, etc.)

---

## 🏗️ Rôle dans l'Architecture

### Position dans l'Architecture

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│         (AuthController)                 │
│  POST /auth/login → Vérifie MFA          │
│  POST /auth/mfa/setup → Génère secret    │
│  POST /auth/mfa/enable → Active MFA      │
│  POST /auth/mfa/verify → Vérifie code    │
│  POST /auth/mfa/disable → Désactive MFA  │
│  GET /auth/mfa/status → Statut MFA       │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         BUSINESS LAYER                   │
│         (MfaService)                      │
│  - generateSecret() → Génère secret       │
│  - generateQrCodeDataUrl() → QR code      │
│  - verifyCode() → Vérifie code TOTP       │
│  - enableMfa() → Active MFA               │
│  - disableMfa() → Désactive MFA           │
│  (MfaTokenService)                        │
│  - generateMfaToken() → Token temporaire  │
│  - validateMfaToken() → Valide token      │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         MODEL LAYER                     │
│         (User Entity)                    │
│  - mfaEnabled (boolean)                 │
│  - mfaSecret (string, Base32)            │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATA LAYER                      │
│         (users table)                   │
│  - mfa_enabled (BOOLEAN)                 │
│  - mfa_secret (VARCHAR)                  │
└─────────────────────────────────────────┘
```

### Flux d'Authentification avec MFA

**1. Login avec MFA Activé:**
```
User → POST /auth/login { username, password }
  ↓
AuthController vérifie credentials
  ↓
Credentials valides ✅
  ↓
Vérifie: mfaService.isMfaEnabled(user)?
  ↓
Si OUI:
  → Génère mfaToken temporaire
  → Retourne { mfaRequired: true, mfaToken: "..." }
  ↓
User → POST /auth/mfa/verify { mfaToken, code }
  ↓
AuthController vérifie code TOTP
  ↓
Code valide ✅
  → Génère access token et refresh token
  → Retourne tokens finaux
```

**2. Login sans MFA:**
```
User → POST /auth/login { username, password }
  ↓
AuthController vérifie credentials
  ↓
Credentials valides ✅
  ↓
Vérifie: mfaService.isMfaEnabled(user)?
  ↓
Si NON:
  → Génère directement access token et refresh token
  → Retourne tokens finaux
```

---

## 💻 Implémentation Spring Boot

### 1. Modèle de Données

**Fichier:** `User.java`

```java
@Entity
@Table(name = "users")
public class User {
    
    @Column(name = "mfa_enabled")
    private Boolean mfaEnabled = false;  // MFA activé ou non
    
    @Column(name = "mfa_secret")
    private String mfaSecret;  // Secret TOTP (Base32, stocké en clair)
}
```

**⚠️ Important:** Le secret est stocké en clair car il est nécessaire pour générer et vérifier les codes TOTP. Il ne doit jamais être exposé dans les réponses API.

### 2. Service MFA

**Fichier:** `MfaService.java`

```java
@Service
@Slf4j
public class MfaService {

    @Value("${app.name:Auth Service}")
    private String appName;

    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;

    public MfaService() {
        // Initialisation des composants TOTP
        this.secretGenerator = new DefaultSecretGenerator();  // Génère secrets Base32
        this.qrGenerator = new ZxingPngQrGenerator();  // Génère QR codes PNG
        this.codeVerifier = new DefaultCodeVerifier(
            new DefaultCodeGenerator(),  // Génère codes TOTP
            new SystemTimeProvider()      // Fournit le temps actuel
        );
    }

    /**
     * Génère un nouveau secret TOTP (Base32)
     */
    public String generateSecret() {
        return secretGenerator.generate();  // Ex: "JBSWY3DPEHPK3PXP"
    }

    /**
     * Génère un QR code en Base64 pour le secret
     */
    public String generateQrCodeDataUrl(String secret, String username) {
        try {
            // Créer les données TOTP pour le QR code
            QrData qrData = new QrData.Builder()
                    .label(username)                    // Label dans l'app
                    .secret(secret)                     // Secret TOTP
                    .issuer(appName)                    // Nom de l'application
                    .algorithm(HashingAlgorithm.SHA1)   // Algorithme HMAC-SHA1
                    .digits(6)                          // 6 chiffres
                    .period(30)                         // 30 secondes
                    .build();

            // Générer le QR code en PNG
            byte[] qrCodeImage = qrGenerator.generate(qrData);
            
            // Convertir en Base64 pour data URL
            String base64Image = Base64.getEncoder().encodeToString(qrCodeImage);
            return "data:image/png;base64," + base64Image;
        } catch (QrGenerationException e) {
            log.error("Error generating QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Vérifie si un code TOTP est valide pour un secret
     */
    public boolean verifyCode(String secret, String code) {
        try {
            return codeVerifier.isValidCode(secret, code);
        } catch (Exception e) {
            log.error("Error verifying MFA code", e);
            return false;
        }
    }

    /**
     * Active MFA pour un utilisateur
     */
    public void enableMfa(User user, String secret) {
        user.setMfaEnabled(true);
        user.setMfaSecret(secret);
        log.info("MFA enabled for user: {}", user.getUsername());
    }

    /**
     * Désactive MFA pour un utilisateur
     */
    public void disableMfa(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);  // Supprimer le secret
        log.info("MFA disabled for user: {}", user.getUsername());
    }

    /**
     * Vérifie si MFA est activé pour un utilisateur
     */
    public boolean isMfaEnabled(User user) {
        return user.getMfaEnabled() != null 
            && user.getMfaEnabled() 
            && user.getMfaSecret() != null 
            && !user.getMfaSecret().isEmpty();
    }
}
```

### 3. Service MFA Token (Temporaire)

**Fichier:** `MfaTokenService.java`

```java
@Service
public class MfaTokenService {
    
    private final Map<String, MfaTokenData> mfaTokens = new ConcurrentHashMap<>();
    private static final long MFA_TOKEN_EXPIRY_MS = 5 * 60 * 1000;  // 5 minutes
    
    /**
     * Génère un token temporaire pour la vérification MFA
     */
    public String generateMfaToken(User user, String ipAddress, String userAgent) {
        String token = UUID.randomUUID().toString();
        MfaTokenData data = new MfaTokenData(user, ipAddress, userAgent, System.currentTimeMillis());
        mfaTokens.put(token, data);
        return token;
    }
    
    /**
     * Valide un token MFA temporaire
     */
    public MfaTokenData validateMfaToken(String token) {
        MfaTokenData data = mfaTokens.get(token);
        if (data == null) {
            return null;  // Token n'existe pas
        }
        
        // Vérifier expiration (5 minutes)
        if (System.currentTimeMillis() - data.getCreatedAt() > MFA_TOKEN_EXPIRY_MS) {
            mfaTokens.remove(token);
            return null;  // Token expiré
        }
        
        return data;
    }
    
    /**
     * Supprime un token MFA
     */
    public void removeMfaToken(String token) {
        mfaTokens.remove(token);
    }
    
    @Getter
    @AllArgsConstructor
    public static class MfaTokenData {
        private final User user;
        private final String ipAddress;
        private final String userAgent;
        private final long createdAt;
    }
}
```

### 4. Utilisation dans AuthController

**Fichier:** `AuthController.java`

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
    // ... vérification credentials ...
    
    // 🔒 STEP 4: Vérifier si MFA est activé
    if (mfaService.isMfaEnabled(user)) {
        // MFA activé → Générer token temporaire
        String mfaToken = mfaTokenService.generateMfaToken(user, ipAddress, userAgent);
        
        return ResponseEntity.status(200).body(MfaLoginResponse.builder()
                .mfaRequired(true)
                .mfaToken(mfaToken)
                .build());
    }
    
    // MFA non activé → Générer tokens directement
    // ...
}
```

---

## 🌐 Endpoints et Flux

### 1. POST /auth/mfa/setup - Configuration MFA

**Description:** Génère un secret et un QR code pour configurer MFA

**Request:**
```http
POST http://localhost:8081/auth/mfa/setup
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Response (200 OK):**
```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "manualEntryKey": "JBSW Y3DP EHPK 3PXP"
}
```

**Traitement Interne:**
1. Extraction du username depuis l'access token
2. `MfaService.generateSecret()` → Génère secret Base32
3. `MfaService.generateQrCodeDataUrl()` → Génère QR code
4. Formatage du secret pour saisie manuelle (espaces tous les 4 caractères)
5. Retour du secret, QR code, et clé manuelle

**⚠️ Important:** Le secret n'est PAS encore sauvegardé. L'utilisateur doit vérifier le code avant activation.

---

### 2. POST /auth/mfa/enable-with-secret - Activation MFA

**Description:** Active MFA après vérification du code

**Request:**
```http
POST http://localhost:8081/auth/mfa/enable-with-secret
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{
  "secret": "JBSWY3DPEHPK3PXP",
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

**Traitement Interne:**
1. Extraction du username depuis l'access token
2. Vérification du code TOTP: `mfaService.verifyCode(secret, code)`
3. Si valide:
   - `mfaService.enableMfa(user, secret)` → Active MFA
   - Sauvegarde en base: `mfa_enabled = true`, `mfa_secret = secret`
   - Log `MFA_ENABLED`
4. Si invalide → 401 Unauthorized

**Response (401 Unauthorized) - Code Invalide:**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

---

### 3. POST /auth/mfa/disable - Désactivation MFA

**Description:** Désactive MFA pour un utilisateur

**Request:**
```http
POST http://localhost:8081/auth/mfa/disable
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Response (200 OK):**
```json
{
  "message": "MFA disabled successfully",
  "username": "admin"
}
```

**Traitement Interne:**
1. Extraction du username depuis l'access token
2. Vérification que MFA est activé
3. `mfaService.disableMfa(user)` → Désactive MFA
4. Sauvegarde en base: `mfa_enabled = false`, `mfa_secret = null`
5. Log `MFA_DISABLED`

---

### 4. POST /auth/login - Login avec MFA

**Description:** Login qui retourne un mfaToken si MFA est activé

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password123"
}
```

**Response (200 OK) - MFA Activé:**
```json
{
  "mfaRequired": true,
  "mfaToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Traitement Interne:**
1. Vérification des credentials ✅
2. Vérification: `mfaService.isMfaEnabled(user)?`
3. Si OUI:
   - Génération d'un `mfaToken` temporaire (valide 5 minutes)
   - Retour de `mfaRequired: true` et `mfaToken`
4. Si NON:
   - Génération directe des tokens finaux
   - Retour de `token` et `refreshToken`

**Response (200 OK) - MFA Non Activé:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

### 5. POST /auth/mfa/verify - Vérification Code MFA

**Description:** Vérifie le code TOTP et complète la connexion

**Request:**
```http
POST http://localhost:8081/auth/mfa/verify
Content-Type: application/json

{
  "mfaToken": "550e8400-e29b-41d4-a716-446655440000",
  "code": "123456"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "660e8400-e29b-41d4-a716-446655440001"
}
```

**Traitement Interne:**
1. Validation du `mfaToken` (existe et non expiré?)
2. Récupération de l'utilisateur depuis le token
3. Vérification du code TOTP: `mfaService.verifyCode(user.mfaSecret, code)`
4. Si valide:
   - Génération des tokens finaux (access + refresh)
   - Suppression du `mfaToken` temporaire
   - Log `MFA_VERIFICATION_SUCCESS`
   - Retour des tokens
5. Si invalide:
   - Log `MFA_VERIFICATION_FAILED`
   - Suppression du `mfaToken`
   - 401 Unauthorized

**Response (401 Unauthorized) - Code Invalide:**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

**Response (401 Unauthorized) - Token Invalide/Expiré:**
```json
{
  "error": "Invalid or expired MFA token",
  "message": "Please login again"
}
```

---

### 6. GET /auth/mfa/status - Statut MFA

**Description:** Vérifie si MFA est activé pour l'utilisateur

**Request:**
```http
GET http://localhost:8081/auth/mfa/status
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Response (200 OK):**
```json
{
  "username": "admin",
  "mfaEnabled": true
}
```

**Traitement Interne:**
1. Extraction du username depuis l'access token
2. Récupération de l'utilisateur
3. Vérification: `mfaService.isMfaEnabled(user)`
4. Retour du statut

---

## 🚫 Contraintes et Limitations

### Contraintes Configurées

1. **Algorithme TOTP:**
   - **Algorithme:** HMAC-SHA1
   - **Digits:** 6 chiffres
   - **Period:** 30 secondes
   - **Format Secret:** Base32

2. **Token MFA Temporaire:**
   - **Durée:** 5 minutes
   - **Stockage:** Mémoire (ConcurrentHashMap)
   - **Format:** UUID

3. **QR Code:**
   - **Format:** PNG en Base64
   - **Taille:** Variable (dépend du secret)

### Limitations

1. **Secret Stocké en Clair:**
   - Le secret TOTP est stocké en clair en base
   - Nécessaire pour générer/vérifier les codes
   - **Solution:** Chiffrer le secret si nécessaire

2. **Token MFA en Mémoire:**
   - Perdu en cas de redémarrage du serveur
   - **Solution:** Stocker en base ou cache distribué (Redis)

3. **Pas de Backup Codes:**
   - Si l'utilisateur perd son téléphone, il ne peut plus se connecter
   - **Solution:** Générer des codes de secours

4. **Pas de SMS/Email MFA:**
   - Seulement TOTP (application authentificatrice)
   - **Solution:** Ajouter SMS/Email comme alternative

### Améliorations Possibles

1. **Backup Codes:**
   - Générer 10 codes de secours lors de l'activation
   - Stocker en hash (comme les mots de passe)
   - Permettre la connexion avec un backup code

2. **SMS/Email MFA:**
   - Envoyer un code par SMS/Email
   - Alternative à TOTP

3. **Chiffrement du Secret:**
   - Chiffrer `mfa_secret` en base
   - Déchiffrer lors de la vérification

4. **Token MFA Persistant:**
   - Stocker en base au lieu de la mémoire
   - Survit aux redémarrages

---

## ⚙️ Configuration TOTP

### Paramètres TOTP

**Fichier:** `MfaService.java`

```java
QrData qrData = new QrData.Builder()
    .label(username)                    // Label dans l'app
    .secret(secret)                     // Secret Base32
    .issuer(appName)                    // Nom de l'app
    .algorithm(HashingAlgorithm.SHA1)   // HMAC-SHA1
    .digits(6)                           // 6 chiffres
    .period(30)                          // 30 secondes
    .build();
```

**Explication:**
- **Algorithm:** HMAC-SHA1 (standard TOTP)
- **Digits:** 6 chiffres (standard)
- **Period:** 30 secondes (standard, peut être 60s)

### Format du Secret

**Base32:**
```
JBSWY3DPEHPK3PXP
```

**Format pour Saisie Manuelle:**
```
JBSW Y3DP EHPK 3PXP
```

### Format URI TOTP

Le QR code contient une URI au format:
```
otpauth://totp/Auth%20Service:admin?secret=JBSWY3DPEHPK3PXP&issuer=Auth%20Service&algorithm=SHA1&digits=6&period=30
```

**Composants:**
- `otpauth://totp/` - Protocole TOTP
- `Auth%20Service:admin` - Issuer:Username
- `secret=...` - Secret Base32
- `issuer=...` - Nom de l'application
- `algorithm=SHA1` - Algorithme
- `digits=6` - Nombre de chiffres
- `period=30` - Période en secondes

---

## 📱 Applications Authentificatrices Compatibles

- **Google Authenticator** (iOS, Android)
- **Microsoft Authenticator** (iOS, Android)
- **Authy** (iOS, Android, Desktop)
- **1Password** (iOS, Android, Desktop)
- **LastPass Authenticator** (iOS, Android)

**Toutes ces applications supportent le standard TOTP RFC 6238.**

---

## 🎯 Résumé

### Concept
MFA ajoute une couche supplémentaire de sécurité en exigeant un code TOTP en plus du mot de passe.

### Rôle dans l'Architecture
- **Modèle:** `User` - Stocke `mfa_enabled` et `mfa_secret`
- **Service:** `MfaService` - Génération secret, QR code, vérification
- **Service:** `MfaTokenService` - Tokens temporaires pour login
- **Contrôleur:** `AuthController` - 6 endpoints MFA

### Implémentation
- **Type:** TOTP (Time-based One-Time Password)
- **Algorithme:** HMAC-SHA1
- **Configuration:** 6 chiffres, 30 secondes
- **Secret:** Base32, stocké en clair

### Endpoints
- `POST /auth/mfa/setup` - Génère secret et QR code
- `POST /auth/mfa/enable-with-secret` - Active MFA
- `POST /auth/mfa/disable` - Désactive MFA
- `POST /auth/mfa/verify` - Vérifie code lors du login
- `GET /auth/mfa/status` - Statut MFA
- `POST /auth/login` - Retourne mfaToken si MFA activé

### Sécurité
- ✅ Double authentification (mot de passe + code)
- ✅ Code change toutes les 30 secondes
- ✅ Résistant au phishing
- ✅ Protection même si mot de passe volé

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
