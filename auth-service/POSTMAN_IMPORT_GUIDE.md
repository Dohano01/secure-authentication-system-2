# 📥 Guide d'Importation - Collection Postman

## 🚀 Importation Rapide

### Méthode 1: Import depuis Fichier JSON

1. **Ouvrez Postman**
2. **Cliquez sur "Import"** (bouton en haut à gauche)
3. **Sélectionnez "Upload Files"**
4. **Choisissez le fichier:** `POSTMAN_COLLECTION.json`
5. **Cliquez "Import"**

La collection "Auth Service API" apparaîtra dans votre sidebar gauche.

---

### Méthode 2: Import depuis URL (si hébergé)

1. **Cliquez sur "Import"**
2. **Sélectionnez l'onglet "Link"**
3. **Collez l'URL de la collection**
4. **Cliquez "Continue"** puis **"Import"**

---

## ⚙️ Configuration de l'Environnement

### Étape 1: Créer l'Environnement

1. **Cliquez sur l'icône "Environments"** (œil en haut à droite)
2. **Cliquez sur "+"** pour créer un nouvel environnement
3. **Nommez-le:** `Auth Service Local`

### Étape 2: Ajouter les Variables

Ajoutez ces variables dans l'environnement:

| Variable | Initial Value | Current Value | Type |
|----------|--------------|---------------|------|
| `base_url` | `http://localhost:8081` | `http://localhost:8081` | default |
| `access_token` | (vide) | (vide) | secret |
| `refresh_token` | (vide) | (vide) | secret |
| `mfa_token` | (vide) | (vide) | secret |
| `mfa_secret` | (vide) | (vide) | secret |
| `username` | `admin` | `admin` | default |
| `password` | `admin123` | `admin123` | secret |

### Étape 3: Sélectionner l'Environnement

1. **Cliquez sur le dropdown** en haut à droite (à côté de "Environments")
2. **Sélectionnez:** `Auth Service Local`

---

## 🎯 Utilisation de la Collection

### Structure de la Collection

```
Auth Service API
├── 📁 Authentication
│   ├── Register
│   ├── Login
│   ├── Validate Token
│   ├── Login History
│   └── Unlock Account
├── 📁 Refresh Tokens
│   ├── Refresh Token
│   ├── Logout
│   ├── Logout All
│   └── View Sessions
└── 📁 MFA
    ├── Setup MFA
    ├── Enable MFA
    ├── Disable MFA
    ├── Verify MFA
    └── MFA Status
```

### Exécution des Requêtes

1. **Développez la collection** dans le sidebar
2. **Sélectionnez une requête** (ex: "Login")
3. **Vérifiez les variables** (elles seront automatiquement remplacées)
4. **Cliquez "Send"**

### Scripts Automatiques

La collection inclut des scripts qui:
- ✅ Sauvegardent automatiquement les tokens dans les variables
- ✅ Testent les réponses
- ✅ Nettoient les tokens après logout

**Exemple:** Après un login réussi, `access_token` et `refresh_token` sont automatiquement sauvegardés et utilisables dans les autres requêtes.

---

## 🔄 Workflow Recommandé

### 1. Premier Setup

```
1. Import collection
2. Créer environnement
3. Configurer variables
4. Tester: Register → Login
```

### 2. Test Complet

```
1. Register (créer utilisateur)
2. Login (obtenir tokens)
3. Validate Token (vérifier)
4. Refresh Token (renouveler)
5. View Sessions (voir sessions)
6. Setup MFA (si nécessaire)
7. Enable MFA (si nécessaire)
8. Login with MFA (si MFA activé)
```

---

## 📝 Notes Importantes

### Variables Automatiques

Les scripts sauvegardent automatiquement:
- `access_token` - Après login ou refresh
- `refresh_token` - Après login ou refresh
- `mfa_token` - Après login avec MFA requis
- `mfa_secret` - Après setup MFA

### Tokens Expirés

Si vous recevez `401 Unauthorized`:
1. Refaites login pour obtenir de nouveaux tokens
2. Ou utilisez refresh token pour renouveler

### MFA Codes

Les codes TOTP changent toutes les 30 secondes. Utilisez toujours le code actuel de Google Authenticator.

---

**Collection prête à l'emploi!** 🎉
