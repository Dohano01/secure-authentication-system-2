# ⚡ Quick Start - Postman API Testing

## 🚀 Démarrage Rapide (5 minutes)

### Étape 1: Importer la Collection (30 secondes)

1. Ouvrez Postman
2. Cliquez **"Import"**
3. Sélectionnez **`POSTMAN_COLLECTION.json`**
4. Cliquez **"Import"**

✅ Collection importée!

---

### Étape 2: Créer l'Environnement (1 minute)

1. Cliquez sur **"Environments"** (icône œil)
2. Cliquez **"+"**
3. Nom: `Auth Service Local`
4. Ajoutez ces variables:

```
base_url = http://localhost:8081
username = admin
password = admin123
```

5. Cliquez **"Save"**
6. Sélectionnez l'environnement dans le dropdown

---

### Étape 3: Tester le Login (30 secondes)

1. Développez **"Auth Service API"** → **"Authentication"**
2. Cliquez sur **"Login"**
3. Vérifiez que le body contient:
   ```json
   {
     "username": "{{username}}",
     "password": "{{password}}"
   }
   ```
4. Cliquez **"Send"**

✅ Si succès, les tokens sont automatiquement sauvegardés!

---

### Étape 4: Tester les Autres Endpoints (2 minutes)

**Maintenant vous pouvez tester:**

1. **Validate Token** - Vérifier que le token fonctionne
2. **Refresh Token** - Renouveler le token
3. **View Sessions** - Voir les sessions actives
4. **Login History** - Voir l'historique

**Tous utilisent automatiquement les tokens sauvegardés!**

---

## 🎯 Workflow Complet (10 minutes)

### Test 1: Authentification de Base

```
1. Register → Créer utilisateur
2. Login → Obtenir tokens
3. Validate Token → Vérifier token
4. Login History → Voir historique
```

### Test 2: Refresh Tokens

```
1. Login → Obtenir tokens
2. Refresh Token → Renouveler
3. View Sessions → Voir sessions
4. Logout → Révoquer token
```

### Test 3: MFA (Optionnel)

```
1. Login → Obtenir access token
2. Setup MFA → Générer secret
3. Ajouter dans Google Authenticator
4. Enable MFA → Activer avec code
5. Login → Recevoir mfaToken
6. Verify MFA → Obtenir tokens finaux
```

---

## 💡 Astuces

### Sauvegarde Automatique

Les scripts sauvegardent automatiquement:
- ✅ `access_token` après login/refresh
- ✅ `refresh_token` après login/refresh
- ✅ `mfa_token` si MFA requis
- ✅ `mfa_secret` après setup

### Réutilisation des Tokens

Une fois sauvegardés, les tokens sont automatiquement utilisés dans:
- Validate Token
- View Sessions
- MFA endpoints
- Logout All

### Tests Automatiques

Chaque requête inclut des tests qui:
- ✅ Vérifient le status code
- ✅ Vérifient la structure de la réponse
- ✅ Sauvegardent les données importantes

---

## 🐛 Problèmes Courants

### "Token invalide"
→ Refaites login pour obtenir un nouveau token

### "Variable not found"
→ Vérifiez que l'environnement est sélectionné

### "403 Forbidden"
→ Vérifiez que le token est valide et non expiré

---

**Vous êtes prêt! Commencez par importer la collection.** 🎉
