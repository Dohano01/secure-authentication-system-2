# 📮 Guide Complet Postman - API d'Authentification

## 📋 Table des Matières

1. [Configuration Postman](#configuration-postman)
2. [Variables d'Environnement](#variables-denvironnement)
3. [Endpoints d'Authentification](#endpoints-dauthentification)
4. [Endpoints de Refresh Token](#endpoints-de-refresh-token)
5. [Endpoints MFA](#endpoints-mfa)
6. [Endpoints de Gestion](#endpoints-de-gestion)
7. [Collection Postman](#collection-postman)
8. [Scénarios de Test Complets](#scénarios-de-test-complets)

---

# Configuration Postman

## 📥 Importation de la Collection

### Méthode Rapide

1. **Ouvrez Postman**
2. **Cliquez sur "Import"** (bouton en haut à gauche)
3. **Sélectionnez "Upload Files"**
4. **Choisissez:** `POSTMAN_COLLECTION.json`
5. **Cliquez "Import"**

La collection "Auth Service API" apparaîtra avec tous les endpoints pré-configurés!

**Voir:** `POSTMAN_IMPORT_GUIDE.md` pour guide détaillé.

---

## 🚀 Setup Initial

### 1. Créer un Environnement Postman

**Étapes:**
1. Cliquez sur **"Environments"** (icône œil en haut à droite)
2. Cliquez sur **"+"** pour créer un nouvel environnement
3. Nommez-le: `Auth Service Local`
4. Ajoutez ces variables:

| Variable | Valeur Initiale | Type |
|----------|----------------|------|
| `base_url` | `http://localhost:8081` | default |
| `access_token` | (vide) | secret |
| `refresh_token` | (vide) | secret |
| `mfa_token` | (vide) | secret |
| `username` | `admin` | default |
| `password` | `admin123` | default |

5. Cliquez sur **"Save"**

### 2. Sélectionner l'Environnement

- Cliquez sur le dropdown en haut à droite
- Sélectionnez `Auth Service Local`

---

# Variables d'Environnement

## 📝 Utilisation des Variables

Dans Postman, utilisez les variables avec `{{variable_name}}`:

**Exemple:**
```
{{base_url}}/auth/login
```

**Avantages:**
- Changement facile d'environnement (dev, prod)
- Tokens réutilisables entre requêtes
- Maintenance simplifiée

---

# Endpoints d'Authentification

## 1. 🔐 POST /auth/register

**Description:** Inscrire un nouvel utilisateur

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/register
```

**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "username": "newuser",
  "password": "SecurePassword123!",
  "email": "newuser@example.com",
  "fullName": "New User",
  "roles": ["PATIENT"]
}
```

### Réponses

**✅ 200 OK - Succès:**
```json
{
  "message": "User registered successfully",
  "username": "newuser"
}
```

**❌ 400 Bad Request - Username existe déjà:**
```json
{
  "error": "Username already exists"
}
```

### Tests Postman (Scripts)

**Dans l'onglet "Tests":**
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has username", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('username');
});
```

### Exemples de Test

**Test 1: Inscription utilisateur standard**
```json
{
  "username": "patient1",
  "password": "Patient123!",
  "email": "patient1@example.com",
  "fullName": "Patient One",
  "roles": ["PATIENT"]
}
```

**Test 2: Inscription admin**
```json
{
  "username": "admin2",
  "password": "Admin123!",
  "email": "admin2@example.com",
  "fullName": "Admin Two",
  "roles": ["ADMIN"]
}
```

---

## 2. 🔐 POST /auth/login

**Description:** Se connecter et obtenir des tokens

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/login
```

**Headers:**
```
Content-Type: application/json
User-Agent: PostmanRuntime/7.51.0
X-Forwarded-For: 203.0.113.45
```

**Body (raw JSON):**
```json
{
  "username": "{{username}}",
  "password": "{{password}}"
}
```

### Réponses

**✅ 200 OK - Succès (Sans MFA):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJ0b2tlblZlcnNpb24iOjAsInJvbGVzIjpbIkFETU1OIl0sInN1YiI6IkFETUlOIiwiaWF0IjoxNzY4MzQ2NzY5LCJleHAiOjE3NjgzNTAzNjF9.ESf3ysNwmJgSbem3oC7Qy...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**✅ 200 OK - MFA Requis:**
```json
{
  "mfaRequired": true,
  "mfaToken": "temporary-mfa-token-uuid"
}
```

**❌ 401 Unauthorized - Mauvais mot de passe:**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

**❌ 423 Locked - Compte verrouillé:**
```json
{
  "error": "Account locked",
  "message": "Too many failed login attempts. Account is locked.",
  "remainingLockTimeMinutes": 15,
  "tryAgainIn": "15 minutes"
}
```

### Tests Postman (Scripts)

**Dans l'onglet "Tests":**
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has token or mfaRequired", function () {
    var jsonData = pm.response.json();
    if (jsonData.mfaRequired) {
        pm.expect(jsonData).to.have.property('mfaToken');
        // Save MFA token
        pm.environment.set("mfa_token", jsonData.mfaToken);
    } else {
        pm.expect(jsonData).to.have.property('token');
        pm.expect(jsonData).to.have.property('refreshToken');
        // Save tokens
        pm.environment.set("access_token", jsonData.token);
        pm.environment.set("refresh_token", jsonData.refreshToken);
    }
});
```

### Exemples de Test

**Test 1: Login utilisateur standard**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Test 2: Login avec IP personnalisée**
```
Headers:
X-Forwarded-For: 192.168.1.100
```

---

## 3. 🔍 GET /auth/validate

**Description:** Valider un access token

### Configuration Postman

**Method:** `GET`

**URL:**
```
{{base_url}}/auth/validate
```

**Headers:**
```
Authorization: Bearer {{access_token}}
```

### Réponses

**✅ 200 OK - Token valide:**
```json
{
  "sub": "admin",
  "roles": ["ADMIN"],
  "tokenVersion": 0,
  "iat": 1768346252,
  "exp": 1768349852
}
```

**❌ 401 Unauthorized - Token invalide:**
```json
{
  "error": "Invalid token: JWT signature does not match"
}
```

**❌ 400 Bad Request - Token manquant:**
```json
{
  "error": "Missing token"
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Token contains username", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('sub');
});
```

---

## 4. 📜 GET /auth/login-history/{username}

**Description:** Obtenir l'historique de sécurité d'un utilisateur

### Configuration Postman

**Method:** `GET`

**URL:**
```
{{base_url}}/auth/login-history/{{username}}
```

**Headers:**
```
Content-Type: application/json
```

### Réponses

**✅ 200 OK:**
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
  },
  {
    "id": 2,
    "username": "admin",
    "eventType": "LOGIN_FAILED",
    "ipAddress": "192.168.1.100",
    "userAgent": "PostmanRuntime/7.51.0",
    "details": "Login failed: Invalid password",
    "timestamp": "2026-01-14T00:10:30",
    "success": false
  }
]
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response is an array", function () {
    pm.response.to.be.json;
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.be.an('array');
});
```

---

## 5. 🔓 POST /auth/unlock/{username}

**Description:** Déverrouiller manuellement un compte (admin)

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/unlock/{{username}}
```

**Headers:**
```
Content-Type: application/json
```

**Body:** (vide)

### Réponses

**✅ 200 OK:**
```json
{
  "message": "Account unlocked successfully",
  "username": "admin"
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Account unlocked", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.message).to.include("unlocked");
});
```

---

# Endpoints de Refresh Token

## 6. 🔄 POST /auth/refresh

**Description:** Renouveler l'access token avec un refresh token

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/refresh
```

**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "refreshToken": "{{refresh_token}}"
}
```

### Réponses

**✅ 200 OK:**
```json
{
  "token": "new-access-token-jwt",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**❌ 401 Unauthorized - Token invalide:**
```json
{
  "error": "Invalid refresh token",
  "message": "Refresh token is invalid, expired, or revoked"
}
```

**❌ 400 Bad Request - Token manquant:**
```json
{
  "error": "Invalid request",
  "message": "Refresh token is required"
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("New token received", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('token');
    // Save new token
    pm.environment.set("access_token", jsonData.token);
});
```

---

## 7. 🚪 POST /auth/logout

**Description:** Se déconnecter (révoque le refresh token actuel)

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/logout
```

**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "refreshToken": "{{refresh_token}}"
}
```

### Réponses

**✅ 200 OK:**
```json
{
  "message": "Logged out successfully"
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Logout successful", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.message).to.include("successfully");
    // Clear tokens
    pm.environment.set("refresh_token", "");
});
```

---

## 8. 🚪 POST /auth/logout-all

**Description:** Se déconnecter de tous les appareils

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/logout-all
```

**Headers:**
```
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

**Body:** (vide)

### Réponses

**✅ 200 OK:**
```json
{
  "message": "Logged out from all devices successfully",
  "username": "admin"
}
```

**❌ 401 Unauthorized - Token invalide:**
```json
{
  "error": "Invalid token: ..."
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("All devices logged out", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.message).to.include("all devices");
    // Clear all tokens
    pm.environment.set("access_token", "");
    pm.environment.set("refresh_token", "");
});
```

---

## 9. 📱 GET /auth/sessions

**Description:** Voir toutes les sessions actives

### Configuration Postman

**Method:** `GET`

**URL:**
```
{{base_url}}/auth/sessions
```

**Headers:**
```
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

### Réponses

**✅ 200 OK:**
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
    },
    {
      "id": 2,
      "deviceType": "Mobile",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0 (iPhone...)",
      "createdAt": "2026-01-14T00:15:30",
      "expiryDate": "2026-02-13T00:15:30"
    }
  ]
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Sessions returned", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('sessions');
    pm.expect(jsonData.sessions).to.be.an('array');
});
```

---

# Endpoints MFA

## 10. 🔐 POST /auth/mfa/setup

**Description:** Générer secret et QR code pour MFA

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/mfa/setup
```

**Headers:**
```
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

**Body:** (vide)

### Réponses

**✅ 200 OK:**
```json
{
  "secret": "25MYRF4YKZL2IN7PNK77ELHS5U5AQ5IN",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAV4AAAFeAQAAAADIUEq3AAADJUIEQVR4Xu2a05arMBBEm+PAoZfAUlgaLI2leAkOCTju6aoWRjYyb1481YGFxJWDmv5JHvPf22...",
  "manualEntryKey": "25MY RF4Y KZL2 IN7P NK77 ELHS 5U5A Q5IN"
}
```

**❌ 401 Unauthorized - Token invalide:**
```json
{
  "error": "Invalid token: ..."
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Secret and QR code received", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('secret');
    pm.expect(jsonData).to.have.property('qrCodeDataUrl');
    pm.expect(jsonData).to.have.property('manualEntryKey');
    // Save secret for next step
    pm.environment.set("mfa_secret", jsonData.secret);
});
```

### Instructions

1. **Copiez le `secret`** de la réponse
2. **Ouvrez Google Authenticator** sur votre téléphone
3. **Cliquez sur "+"** → "Entrer une clé de configuration"
4. **Entrez:**
   - Nom: `admin` (ou votre username)
   - Clé: `25MYRF4YKZL2IN7PNK77ELHS5U5AQ5IN` (le secret)
   - Type: Temps
5. **Cliquez "Ajouter"**
6. **Notez le code à 6 chiffres** qui apparaît

---

## 11. 🔐 POST /auth/mfa/enable-with-secret

**Description:** Activer MFA après vérification du code

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/mfa/enable-with-secret
```

**Headers:**
```
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "secret": "{{mfa_secret}}",
  "code": "123456"
}
```

**⚠️ IMPORTANT:** Remplacez `123456` par le code actuel de Google Authenticator!

### Réponses

**✅ 200 OK:**
```json
{
  "message": "MFA enabled successfully",
  "username": "admin"
}
```

**❌ 401 Unauthorized - Code invalide:**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

**❌ 400 Bad Request - Données manquantes:**
```json
{
  "error": "Secret and code are required"
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("MFA enabled", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.message).to.include("enabled");
});
```

### Instructions

1. **Obtenez le code actuel** de Google Authenticator (6 chiffres)
2. **Remplacez `123456`** dans le body par ce code
3. **Utilisez le `secret`** sauvegardé de l'étape précédente
4. **Envoyez la requête**

---

## 12. 🔐 POST /auth/mfa/disable

**Description:** Désactiver MFA

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/mfa/disable
```

**Headers:**
```
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

**Body:** (vide)

### Réponses

**✅ 200 OK:**
```json
{
  "message": "MFA disabled successfully",
  "username": "admin"
}
```

**❌ 400 Bad Request - MFA non activé:**
```json
{
  "error": "MFA is not enabled",
  "message": "Multi-factor authentication is not enabled for this account"
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("MFA disabled", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.message).to.include("disabled");
});
```

---

## 13. 🔐 POST /auth/mfa/verify

**Description:** Vérifier le code MFA pendant le login

### Configuration Postman

**Method:** `POST`

**URL:**
```
{{base_url}}/auth/mfa/verify
```

**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "mfaToken": "{{mfa_token}}",
  "code": "123456"
}
```

**⚠️ IMPORTANT:** 
- Utilisez le `mfaToken` reçu lors du login
- Utilisez le code actuel de Google Authenticator

### Réponses

**✅ 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**❌ 401 Unauthorized - Code invalide:**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

**❌ 401 Unauthorized - Token expiré:**
```json
{
  "error": "Invalid or expired MFA token",
  "message": "Please login again"
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Tokens received", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('token');
    pm.expect(jsonData).to.have.property('refreshToken');
    // Save tokens
    pm.environment.set("access_token", jsonData.token);
    pm.environment.set("refresh_token", jsonData.refreshToken);
});
```

---

## 14. 🔍 GET /auth/mfa/status

**Description:** Vérifier si MFA est activé

### Configuration Postman

**Method:** `GET`

**URL:**
```
{{base_url}}/auth/mfa/status
```

**Headers:**
```
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

### Réponses

**✅ 200 OK:**
```json
{
  "username": "admin",
  "mfaEnabled": true
}
```

**❌ 401 Unauthorized - Token invalide:**
```json
{
  "error": "Invalid token: ..."
}
```

### Tests Postman (Scripts)

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("MFA status returned", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('mfaEnabled');
    pm.expect(jsonData.mfaEnabled).to.be.a('boolean');
});
```

---

# Collection Postman

## 📦 Export de Collection

Créez une collection Postman avec tous ces endpoints. Voici la structure recommandée:

### Organisation de la Collection

```
Auth Service API
├── 📁 Authentication
│   ├── Register
│   ├── Login
│   ├── Validate Token
│   └── Login History
├── 📁 Refresh Tokens
│   ├── Refresh Token
│   ├── Logout
│   ├── Logout All
│   └── View Sessions
├── 📁 MFA
│   ├── Setup MFA
│   ├── Enable MFA
│   ├── Disable MFA
│   ├── Verify MFA
│   └── MFA Status
└── 📁 Management
    └── Unlock Account
```

---

# Scénarios de Test Complets

## 🎯 Scénario 1: Flux Complet d'Authentification

### Étape 1: Inscription
```
POST {{base_url}}/auth/register
Body: { username, password, email, fullName, roles }
→ Sauvegarder username dans variable
```

### Étape 2: Login
```
POST {{base_url}}/auth/login
Body: { username, password }
→ Sauvegarder token et refreshToken
```

### Étape 3: Valider Token
```
GET {{base_url}}/auth/validate
Headers: Authorization: Bearer {{access_token}}
→ Vérifier que token est valide
```

### Étape 4: Voir Historique
```
GET {{base_url}}/auth/login-history/{{username}}
→ Vérifier que login est loggé
```

---

## 🎯 Scénario 2: Protection Brute-Force

### Étape 1: Tentatives Échouées
```
POST {{base_url}}/auth/login
Body: { username: "admin", password: "wrong1" }
→ remainingAttempts: 4

POST {{base_url}}/auth/login
Body: { username: "admin", password: "wrong2" }
→ remainingAttempts: 3

POST {{base_url}}/auth/login
Body: { username: "admin", password: "wrong3" }
→ remainingAttempts: 2, warning apparaît

POST {{base_url}}/auth/login
Body: { username: "admin", password: "wrong4" }
→ remainingAttempts: 1, warning apparaît

POST {{base_url}}/auth/login
Body: { username: "admin", password: "wrong5" }
→ 423 Locked, account_locked: true
```

### Étape 2: Tentative avec Compte Verrouillé
```
POST {{base_url}}/auth/login
Body: { username: "admin", password: "correct" }
→ 423 Locked, remainingLockTimeMinutes: 15
```

### Étape 3: Déverrouiller
```
POST {{base_url}}/auth/unlock/admin
→ 200 OK, account unlocked
```

### Étape 4: Login Réussi
```
POST {{base_url}}/auth/login
Body: { username: "admin", password: "correct" }
→ 200 OK, tokens reçus
```

---

## 🎯 Scénario 3: Refresh Token Flow

### Étape 1: Login Initial
```
POST {{base_url}}/auth/login
→ Sauvegarder access_token et refresh_token
```

### Étape 2: Valider Token Initial
```
GET {{base_url}}/auth/validate
Headers: Authorization: Bearer {{access_token}}
→ 200 OK, token valide
```

### Étape 3: Refresh Token
```
POST {{base_url}}/auth/refresh
Body: { refreshToken: "{{refresh_token}}" }
→ 200 OK, nouveau access_token
```

### Étape 4: Valider Nouveau Token
```
GET {{base_url}}/auth/validate
Headers: Authorization: Bearer {{access_token}}
→ 200 OK, nouveau token valide
```

### Étape 5: Logout
```
POST {{base_url}}/auth/logout
Body: { refreshToken: "{{refresh_token}}" }
→ 200 OK, logged out
```

### Étape 6: Tentative Refresh Après Logout
```
POST {{base_url}}/auth/refresh
Body: { refreshToken: "{{refresh_token}}" }
→ 401 Unauthorized, token révoqué
```

---

## 🎯 Scénario 4: MFA Flow Complet

### Étape 1: Setup MFA
```
POST {{base_url}}/auth/mfa/setup
Headers: Authorization: Bearer {{access_token}}
→ Sauvegarder secret
→ Ajouter dans Google Authenticator
```

### Étape 2: Activer MFA
```
POST {{base_url}}/auth/mfa/enable-with-secret
Headers: Authorization: Bearer {{access_token}}
Body: { secret: "{{mfa_secret}}", code: "123456" }
→ Utiliser code actuel de Google Authenticator
→ 200 OK, MFA enabled
```

### Étape 3: Vérifier Statut MFA
```
GET {{base_url}}/auth/mfa/status
Headers: Authorization: Bearer {{access_token}}
→ 200 OK, mfaEnabled: true
```

### Étape 4: Login avec MFA
```
POST {{base_url}}/auth/login
Body: { username, password }
→ 200 OK, mfaRequired: true, mfaToken reçu
→ Sauvegarder mfaToken
```

### Étape 5: Vérifier Code MFA
```
POST {{base_url}}/auth/mfa/verify
Body: { mfaToken: "{{mfa_token}}", code: "123456" }
→ Utiliser code actuel de Google Authenticator
→ 200 OK, tokens finaux reçus
```

### Étape 6: Désactiver MFA
```
POST {{base_url}}/auth/mfa/disable
Headers: Authorization: Bearer {{access_token}}
→ 200 OK, MFA disabled
```

---

## 🎯 Scénario 5: Logout All Devices

### Étape 1: Voir Sessions Actives
```
GET {{base_url}}/auth/sessions
Headers: Authorization: Bearer {{access_token}}
→ Voir toutes les sessions (ex: 3 sessions)
```

### Étape 2: Logout All
```
POST {{base_url}}/auth/logout-all
Headers: Authorization: Bearer {{access_token}}
→ 200 OK, all devices logged out
```

### Étape 3: Vérifier Sessions
```
GET {{base_url}}/auth/sessions
Headers: Authorization: Bearer {{access_token}}
→ 401 Unauthorized (token invalidé)
```

### Étape 4: Nouveau Login Requis
```
POST {{base_url}}/auth/login
Body: { username, password }
→ Nouveau login nécessaire
```

---

# 🧪 Tests Automatisés Postman

## Collection Runner

### Configuration

1. **Ouvrez la collection**
2. **Cliquez sur "Run"**
3. **Sélectionnez les requêtes à exécuter**
4. **Configurez l'ordre d'exécution**
5. **Cliquez "Run Auth Service API"**

### Ordre Recommandé

1. Register (créer utilisateur de test)
2. Login (obtenir tokens)
3. Validate Token
4. Refresh Token
5. View Sessions
6. Setup MFA
7. Enable MFA
8. Login with MFA
9. Verify MFA
10. View Login History

---

# 📊 Variables Postman Globales

## Script Pre-request (Collection Level)

**Dans l'onglet "Pre-request Script" de la collection:**

```javascript
// Set default base URL if not set
if (!pm.environment.get("base_url")) {
    pm.environment.set("base_url", "http://localhost:8081");
}

// Generate timestamp for unique usernames in tests
pm.environment.set("timestamp", Date.now());
```

## Scripts de Test Globaux

**Dans l'onglet "Tests" de la collection:**

```javascript
// Global test: Check response time
pm.test("Response time is less than 2000ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(2000);
});

// Global test: Check response is JSON
pm.test("Response is JSON", function () {
    pm.response.to.be.json;
});
```

---

# 🔧 Troubleshooting Postman

## Problème: Variables ne se remplissent pas

**Solution:**
1. Vérifiez que l'environnement est sélectionné
2. Vérifiez l'orthographe: `{{variable_name}}`
3. Vérifiez que la variable existe dans l'environnement

## Problème: Token expiré

**Solution:**
1. Refaites login pour obtenir un nouveau token
2. Le token expire après 1 heure
3. Utilisez refresh token pour obtenir un nouveau token

## Problème: 403 Forbidden

**Solution:**
1. Vérifiez que le token est valide
2. Vérifiez le format: `Authorization: Bearer <token>`
3. Vérifiez que le token n'est pas expiré

## Problème: MFA code invalide

**Solution:**
1. Le code change toutes les 30 secondes
2. Utilisez le code actuel de Google Authenticator
3. Vérifiez que l'heure de votre téléphone est synchronisée

---

# 📝 Checklist de Test Postman

## Avant de Commencer

- [ ] Environnement Postman créé
- [ ] Variables configurées
- [ ] Application Spring Boot démarrée
- [ ] Base de données accessible
- [ ] Port 8081 disponible

## Tests de Base

- [ ] ✅ Register - Créer utilisateur
- [ ] ✅ Login - Obtenir tokens
- [ ] ✅ Validate - Valider token
- [ ] ✅ Login History - Voir historique

## Tests Refresh Token

- [ ] ✅ Refresh - Renouveler token
- [ ] ✅ Logout - Révoquer token
- [ ] ✅ Logout All - Révoquer tous
- [ ] ✅ Sessions - Voir sessions

## Tests MFA

- [ ] ✅ Setup - Générer secret
- [ ] ✅ Enable - Activer MFA
- [ ] ✅ Status - Vérifier statut
- [ ] ✅ Login with MFA - Login en 2 étapes
- [ ] ✅ Verify - Vérifier code
- [ ] ✅ Disable - Désactiver MFA

## Tests Sécurité

- [ ] ✅ Brute-Force - 5 tentatives = lock
- [ ] ✅ Account Locked - Impossible de login
- [ ] ✅ Auto-Unlock - Déverrouillage après 15 min
- [ ] ✅ IP Tracking - IP enregistrée
- [ ] ✅ Device Detection - Device détecté

---

# 🎯 Quick Reference

## Endpoints par Catégorie

### Authentification
- `POST /auth/register` - Inscription
- `POST /auth/login` - Connexion
- `GET /auth/validate` - Valider token
- `GET /auth/login-history/{username}` - Historique
- `POST /auth/unlock/{username}` - Déverrouiller

### Refresh Tokens
- `POST /auth/refresh` - Renouveler token
- `POST /auth/logout` - Déconnexion
- `POST /auth/logout-all` - Déconnexion globale
- `GET /auth/sessions` - Sessions actives

### MFA
- `POST /auth/mfa/setup` - Setup MFA
- `POST /auth/mfa/enable-with-secret` - Activer MFA
- `POST /auth/mfa/disable` - Désactiver MFA
- `POST /auth/mfa/verify` - Vérifier code
- `GET /auth/mfa/status` - Statut MFA

## Codes de Statut

- `200 OK` - Succès
- `400 Bad Request` - Requête invalide
- `401 Unauthorized` - Non autorisé
- `403 Forbidden` - Accès refusé
- `423 Locked` - Compte verrouillé
- `404 Not Found` - Ressource non trouvée

---

# 📊 Résumé des Endpoints

## 📈 Statistiques

- **Total Endpoints:** 14
- **Endpoints Authentification:** 5
- **Endpoints Refresh Token:** 4
- **Endpoints MFA:** 5

## 🔑 Codes de Statut Utilisés

| Code | Signification | Endpoints |
|------|---------------|-----------|
| 200 | Succès | Tous les endpoints de succès |
| 400 | Requête invalide | Register, MFA enable |
| 401 | Non autorisé | Login, Validate, Refresh, MFA |
| 403 | Accès refusé | MFA status (si token invalide) |
| 423 | Compte verrouillé | Login (brute-force) |
| 404 | Non trouvé | Login history, Unlock |

## 📝 Variables Postman Utilisées

| Variable | Utilisée Dans | Description |
|----------|---------------|-------------|
| `base_url` | Tous | URL de base de l'API |
| `access_token` | Validate, Sessions, MFA | Token JWT d'accès |
| `refresh_token` | Refresh, Logout | Token de rafraîchissement |
| `mfa_token` | Verify MFA | Token temporaire MFA |
| `mfa_secret` | Enable MFA | Secret TOTP |
| `username` | Login, History | Nom d'utilisateur |
| `password` | Login | Mot de passe |

## 🎯 Quick Start

1. **Importez** `POSTMAN_COLLECTION.json`
2. **Créez** l'environnement avec variables
3. **Exécutez** "Register" pour créer un utilisateur
4. **Exécutez** "Login" pour obtenir tokens
5. **Testez** les autres endpoints!

---

**Guide créé le:** 2026-01-14
**Version:** 1.0
**Total Endpoints:** 14
**Collection Postman:** `POSTMAN_COLLECTION.json`
