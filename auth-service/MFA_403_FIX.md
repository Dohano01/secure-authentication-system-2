# 🔧 Fix: 403 Forbidden sur Endpoints MFA

## ❌ Problème

Vous recevez `403 Forbidden` quand vous essayez d'accéder à `/auth/mfa/status` ou autres endpoints MFA.

## ✅ Solutions

### Solution 1: Vérifier que le Token est Valide

**Le problème le plus courant:** Le token JWT est expiré ou invalide.

**Vérification:**
1. **Vérifiez que vous utilisez un token récent:**
   - Les tokens expirent après 1 heure (par défaut)
   - Si vous avez fait login il y a plus d'1 heure, refaites login

2. **Testez votre token:**
   ```http
   GET http://localhost:8081/auth/validate
   Authorization: Bearer <your-token>
   ```
   
   Si ça retourne une erreur, votre token est invalide.

3. **Refaites login pour obtenir un nouveau token:**
   ```http
   POST http://localhost:8081/auth/login
   Content-Type: application/json
   
   {
     "username": "admin",
     "password": "admin123"
   }
   ```

### Solution 2: Vérifier le Format du Header

**Assurez-vous que le header est correct:**
- ✅ **Correct:** `Authorization: Bearer eyJhbGci...`
- ❌ **Faux:** `Authorization: eyJhbGci...` (manque "Bearer ")
- ❌ **Faux:** `Authorization: Bearer` (manque le token)

**Dans Postman:**
- Header name: `Authorization`
- Header value: `Bearer <votre-token-complet>`
- Pas d'espaces supplémentaires

### Solution 3: Redémarrer l'Application

**Si vous venez d'ajouter les endpoints MFA:**
1. Arrêtez l'application (Ctrl+C)
2. Redémarrez-la
3. Les nouveaux endpoints seront disponibles

### Solution 4: Vérifier que les Dépendances sont Installées

**Assurez-vous que Maven a téléchargé les dépendances:**
```bash
cd auth-service
mvn clean install
```

**Vérifiez dans `pom.xml` que ces dépendances sont présentes:**
```xml
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
    <version>1.7.1</version>
</dependency>
```

### Solution 5: Vérifier les Logs Spring Boot

**Regardez les logs pour voir l'erreur exacte:**
- Cherchez des erreurs de compilation
- Cherchez des erreurs de validation de token
- Cherchez des erreurs 403 avec détails

## 🧪 Test Step-by-Step

### Étape 1: Obtenir un Token Valide

```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Réponse attendue:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "uuid-here"
}
```

**Copiez le `token` (pas le refreshToken)!**

### Étape 2: Tester le Token

```http
GET http://localhost:8081/auth/validate
Authorization: Bearer <token-from-step-1>
```

**Si ça fonctionne, vous verrez les claims du token.**

### Étape 3: Tester MFA Status

```http
GET http://localhost:8081/auth/mfa/status
Authorization: Bearer <token-from-step-1>
```

**Réponse attendue:**
```json
{
  "username": "admin",
  "mfaEnabled": false
}
```

## 🔍 Vérifications Supplémentaires

### Vérifier que les Colonnes MFA Existent

```sql
SELECT column_name 
FROM information_schema.columns 
WHERE table_name = 'users' 
  AND column_name IN ('mfa_enabled', 'mfa_secret');
```

**Doit retourner 2 lignes.**

### Vérifier les Logs Hibernate

Dans les logs, vous devriez voir:
```
Hibernate: update users set ... mfa_enabled=?, mfa_secret=? ...
```

Si vous ne voyez pas `mfa_enabled` et `mfa_secret`, les colonnes n'existent pas.

## ✅ Checklist

- [ ] Token JWT est récent (moins d'1 heure)
- [ ] Header `Authorization: Bearer <token>` est correct
- [ ] Application redémarrée après ajout des endpoints
- [ ] Dépendances Maven installées
- [ ] Colonnes MFA ajoutées à la base de données
- [ ] Token validé avec `/auth/validate` avant de tester MFA

---

**Une fois ces vérifications faites, les endpoints MFA devraient fonctionner!** 🎉
