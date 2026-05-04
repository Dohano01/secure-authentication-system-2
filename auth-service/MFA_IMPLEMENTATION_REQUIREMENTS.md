# 📋 Requirements pour Implémenter MFA

## 📦 Dépendances Nécessaires

### 1. Bibliothèque TOTP
Pour générer et valider les codes TOTP (Time-based One-Time Password), nous avons besoin d'une bibliothèque.

**Option 1: Google Authenticator Library (Recommandé)**
```xml
<dependency>
    <groupId>com.warrenstrange</groupId>
    <artifactId>googleauth</artifactId>
    <version>1.5.0</version>
</dependency>
```

**Option 2: TOTP Library (Alternative)**
```xml
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
    <version>1.7.1</version>
</dependency>
```

### 2. Bibliothèque QR Code (pour générer QR codes)
```xml
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
</dependency>
```

---

## 🗄️ Modifications Base de Données

### Table `users` - Ajouter colonnes MFA:
```sql
ALTER TABLE users 
ADD COLUMN mfa_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN mfa_secret VARCHAR(255);
```

---

## 📁 Fichiers à Créer

1. **MfaService.java** - Service pour gérer MFA
2. **MfaController.java** - Endpoints MFA
3. **MfaSetupResponse.java** - DTO pour réponse de setup
4. **MfaVerifyRequest.java** - DTO pour vérification
5. **MfaEnableRequest.java** - DTO pour activation

---

## 🔄 Modifications Nécessaires

1. **User.java** - Ajouter champs MFA
2. **AuthController.java** - Modifier login pour vérifier MFA
3. **pom.xml** - Ajouter dépendances

---

## 🎯 Endpoints à Créer

1. `POST /auth/mfa/setup` - Générer secret et QR code
2. `POST /auth/mfa/enable` - Activer MFA avec code de vérification
3. `POST /auth/mfa/disable` - Désactiver MFA
4. `POST /auth/mfa/verify` - Vérifier code MFA (pendant login)
5. `GET /auth/mfa/status` - Vérifier si MFA est activé

---

## 🔐 Flux d'Activation MFA

1. User appelle `/auth/mfa/setup`
2. Système génère un secret et QR code
3. User scanne QR code avec Google Authenticator
4. User entre le code de l'app
5. User appelle `/auth/mfa/enable` avec le code
6. Système vérifie le code et active MFA

---

## 🔐 Flux de Login avec MFA

1. User entre username/password
2. Si MFA activé, retourner `mfaRequired: true` avec `mfaToken`
3. User entre code MFA
4. User appelle `/auth/mfa/verify` avec `mfaToken` et code
5. Si valide, retourner access + refresh tokens
