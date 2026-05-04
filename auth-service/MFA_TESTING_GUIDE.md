# 🔐 Guide de Test - Multi-Factor Authentication (MFA)

## 📋 Prérequis

1. **Exécuter le script SQL** pour ajouter les colonnes MFA:
   ```sql
   -- Voir MFA_SETUP_SQL.sql
   ALTER TABLE users 
   ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN DEFAULT FALSE,
   ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(255);
   ```

2. **Installer une app d'authentification** sur votre téléphone:
   - Google Authenticator
   - Microsoft Authenticator
   - Authy
   - Ou toute app compatible TOTP

3. **Redémarrer l'application** après avoir ajouté les dépendances Maven

---

## 🧪 Tests Step-by-Step

### Test 1: Vérifier le statut MFA (avant activation)

**Request:**
```http
GET http://localhost:8081/auth/mfa/status
Authorization: Bearer <your-access-token>
```

**Expected Response:**
```json
{
  "username": "admin",
  "mfaEnabled": false
}
```

---

### Test 2: Setup MFA - Générer Secret et QR Code

**Request:**
```http
POST http://localhost:8081/auth/mfa/setup
Authorization: Bearer <your-access-token>
```

**Expected Response:**
```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "manualEntryKey": "JBSW Y3DP EHPK 3PXP"
}
```

**Actions:**
1. **Option A - Secret Manuel (Recommandé):**
   - Copiez le `secret` (ex: `25MYRF4YKZL2IN7PNK77ELHS5U5AQ5IN`)
   - Dans Google Authenticator: "+" → "Entrer une clé de configuration"
   - Nom: `admin`, Clé: `25MYRF4YKZL2IN7PNK77ELHS5U5AQ5IN`, Type: Temps
   - Cliquez "Ajouter"

2. **Option B - QR Code:**
   - Copiez le `qrCodeDataUrl` complet
   - Créez un fichier HTML avec `<img src="qrCodeDataUrl">`
   - Ouvrez dans navigateur et scannez
   - **OU** utilisez un décodeur base64 en ligne

**⚠️ IMPORTANT:** 
- Vous ne pouvez PAS ouvrir directement l'URL base64 dans le navigateur
- L'email de votre compte admin n'a PAS besoin d'être le même que l'email de votre téléphone
- Le secret fonctionne avec n'importe quel compte Google Authenticator

---

### Test 3: Activer MFA - Vérifier le Code

**Request:**
```http
POST http://localhost:8081/auth/mfa/enable-with-secret
Authorization: Bearer <your-access-token>
Content-Type: application/json

{
  "secret": "JBSWY3DPEHPK3PXP",
  "code": "123456"
}
```

**⚠️ IMPORTANT:** 
- Utilisez le `secret` de Test 2
- Utilisez le code actuel de votre app d'authentification (6 chiffres)
- Le code change toutes les 30 secondes!

**Expected Response (Success):**
```json
{
  "message": "MFA enabled successfully",
  "username": "admin"
}
```

**Expected Response (Invalid Code):**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

---

### Test 4: Vérifier le Statut MFA (après activation)

**Request:**
```http
GET http://localhost:8081/auth/mfa/status
Authorization: Bearer <your-access-token>
```

**Expected Response:**
```json
{
  "username": "admin",
  "mfaEnabled": true
}
```

---

### Test 5: Login avec MFA Activé

**Step 5.1: Login (Username/Password)**

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Expected Response (MFA Required):**
```json
{
  "mfaRequired": true,
  "mfaToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**⚠️ IMPORTANT:** 
- Vous recevez un `mfaToken` temporaire (valide 5 minutes)
- Pas de `token` ou `refreshToken` encore!

**Step 5.2: Vérifier le Code MFA**

**Request:**
```http
POST http://localhost:8081/auth/mfa/verify
Content-Type: application/json

{
  "mfaToken": "550e8400-e29b-41d4-a716-446655440000",
  "code": "123456"
}
```

**⚠️ IMPORTANT:** 
- Utilisez le `mfaToken` de Step 5.1
- Utilisez le code actuel de votre app (6 chiffres)

**Expected Response (Success):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "9c04da4e-e6e0-4592-bc1c-b5d8fad3dfe2"
}
```

**Expected Response (Invalid Code):**
```json
{
  "error": "Invalid MFA code",
  "message": "The code you entered is incorrect. Please try again."
}
```

---

### Test 6: Désactiver MFA

**Request:**
```http
POST http://localhost:8081/auth/mfa/disable
Authorization: Bearer <your-access-token>
```

**Expected Response:**
```json
{
  "message": "MFA disabled successfully",
  "username": "admin"
}
```

**Vérification:** Après désactivation, le login ne demandera plus MFA.

---

## 🔍 Vérification Base de Données

### Vérifier si MFA est activé:
```sql
SELECT username, mfa_enabled, mfa_secret 
FROM users 
WHERE username = 'admin';
```

**Résultats attendus:**
- `mfa_enabled`: `true` (si activé)
- `mfa_secret`: Secret TOTP (ex: `JBSWY3DPEHPK3PXP`)

---

## 🐛 Troubleshooting

### Problème: "Invalid MFA code"
**Solutions:**
1. Vérifiez que vous utilisez le code actuel (change toutes les 30 secondes)
2. Vérifiez que l'heure de votre téléphone est synchronisée
3. Vérifiez que vous avez scanné le bon QR code ou entré le bon secret

### Problème: "Invalid or expired MFA token"
**Solutions:**
1. Le `mfaToken` expire après 5 minutes
2. Relancez le login (Step 5.1) pour obtenir un nouveau token

### Problème: QR Code ne s'affiche pas
**Solutions:**
1. Utilisez `manualEntryKey` pour entrer le secret manuellement
2. Ou copiez le `secret` et entrez-le dans votre app

---

## ✅ Checklist de Test

- [ ] Test 1: Vérifier statut MFA (false)
- [ ] Test 2: Setup MFA - obtenir secret et QR code
- [ ] Scanner QR code ou entrer secret manuellement
- [ ] Test 3: Activer MFA avec code valide
- [ ] Test 4: Vérifier statut MFA (true)
- [ ] Test 5.1: Login - recevoir mfaToken
- [ ] Test 5.2: Vérifier code MFA - recevoir tokens
- [ ] Test 6: Désactiver MFA
- [ ] Vérifier que login ne demande plus MFA

---

## 📊 Flux Complet

```
1. User → POST /auth/mfa/setup
   ← Secret + QR Code

2. User scanne QR code avec app
   App génère codes TOTP

3. User → POST /auth/mfa/enable-with-secret
   { secret, code }
   ← MFA activé

4. User → POST /auth/login
   { username, password }
   ← mfaToken (si MFA activé)

5. User → POST /auth/mfa/verify
   { mfaToken, code }
   ← Access Token + Refresh Token
```

---

**MFA est maintenant complètement implémenté!** 🎉
