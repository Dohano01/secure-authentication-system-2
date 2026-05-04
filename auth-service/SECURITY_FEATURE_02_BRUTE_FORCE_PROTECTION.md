# 🛡️ Fonctionnalité de Sécurité #2: Protection Brute-Force

## 📚 Table des Matières

1. [Concept et Théorie](#concept-et-théorie)
2. [Pourquoi cette Protection?](#pourquoi-cette-protection)
3. [Rôle dans l'Architecture](#rôle-dans-larchitecture)
4. [Implémentation Spring Boot](#implémentation-spring-boot)
5. [Endpoints et Flux](#endpoints-et-flux)
6. [Contraintes et Limitations](#contraintes-et-limitations)
7. [Scénarios de Test](#scénarios-de-test)

---

## 🎓 Concept et Théorie

### Qu'est-ce qu'une Attaque Brute-Force?

Une **attaque brute-force** est une méthode d'attaque où un attaquant essaie systématiquement toutes les combinaisons possibles de mots de passe jusqu'à trouver le bon.

**Exemple:**
```
Tentative 1: password123
Tentative 2: password124
Tentative 3: password125
...
Tentative 1000: MyPassword123! ← ✅ Succès
```

### Pourquoi C'est Dangereux?

**Sans Protection:**
- Un attaquant peut essayer des milliers de mots de passe par seconde
- Un compte peut être compromis en quelques minutes
- Aucune alerte ou détection

**Avec Protection:**
- Limite le nombre de tentatives
- Verrouille le compte après X tentatives
- Délai de verrouillage empêche les attaques automatisées
- Alertes et logging pour détection

### Mécanisme de Protection

**Principe:**
1. **Compteur:** Suivre le nombre de tentatives échouées
2. **Seuil:** Définir un maximum (ex: 5 tentatives)
3. **Verrouillage:** Bloquer l'accès après le seuil
4. **Délai:** Attendre un certain temps avant déverrouillage
5. **Auto-déverrouillage:** Déverrouiller automatiquement après le délai

---

## 🤔 Pourquoi cette Protection?

### Statistiques d'Attaques

- **90%** des attaques utilisent la force brute
- **80%** des violations de données commencent par une attaque brute-force
- **Millions** de tentatives par jour sur les systèmes non protégés

### Impact Sans Protection

```
Scénario: Attaquant avec 1000 tentatives/seconde
Mots de passe possibles: 10^8 combinaisons
Temps pour trouver: ~27 heures
Résultat: Compte compromis
```

### Impact Avec Protection (5 tentatives, 15 min)

```
Scénario: Attaquant avec 1000 tentatives/seconde
Tentatives autorisées: 5
Temps de verrouillage: 15 minutes
Tentatives après verrouillage: 0 (bloqué)
Résultat: Compte protégé ✅
```

---

## 🏗️ Rôle dans l'Architecture

### Position dans l'Architecture

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│         (AuthController)                 │
│  POST /auth/login                       │
│  - Vérifie account_locked                │
│  - Gère les réponses de verrouillage     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         BUSINESS LAYER                   │
│         (UserService)                    │
│  - handleFailedLogin()                   │
│  - handleSuccessfulLogin()               │
│  - isAccountLocked()                     │
│  - getRemainingLockTime()                │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         MODEL LAYER                     │
│         (User Entity)                    │
│  - failedLoginAttempts (compteur)        │
│  - accountLocked (statut)                │
│  - lockTime (timestamp)                  │
│  - incrementFailedAttempts()              │
│  - isAccountNonLocked()                  │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATA LAYER                      │
│         (users table)                    │
│  - failed_login_attempts                 │
│  - account_locked                        │
│  - lock_time                             │
└─────────────────────────────────────────┘
```

### Flux de Protection

**Tentative de Connexion Échouée:**
```
1. AuthController.login() reçoit requête
2. Vérifie si compte verrouillé → Si oui, refuse
3. Vérifie mot de passe → Échec
4. UserService.handleFailedLogin()
5. User.incrementFailedAttempts()
6. Si attempts >= 5 → account_locked = true, lock_time = now
7. AuditService.logLoginFailed()
8. Retourne 401 avec remainingAttempts
```

**Tentative de Connexion Réussie:**
```
1. AuthController.login() reçoit requête
2. Vérifie si compte verrouillé → Si oui, refuse
3. Vérifie mot de passe → Succès
4. UserService.handleSuccessfulLogin()
5. User.resetFailedAttempts() → attempts = 0, locked = false
6. AuditService.logLoginSuccess()
7. Génère tokens et retourne 200
```

**Auto-Déverrouillage:**
```
1. AuthController.login() reçoit requête
2. User.isAccountNonLocked() est appelé
3. Vérifie: lock_time + 15 minutes < now?
4. Si oui → account_locked = false, attempts = 0
5. Continue avec vérification mot de passe
```

---

## 💻 Implémentation Spring Boot

### 1. Modèle de Données

**Fichier:** `User.java`

```java
@Entity
@Table(name = "users")
public class User {
    
    // ========================================
    // 🔒 BRUTE-FORCE PROTECTION FIELDS
    // ========================================
    
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "account_locked")
    private Boolean accountLocked = false;
    
    @Column(name = "lock_time")
    private LocalDateTime lockTime;
    
    @Column(name = "last_failed_login")
    private LocalDateTime lastFailedLogin;
    
    /**
     * Vérifie si le compte est non verrouillé
     * Inclut la logique d'auto-déverrouillage
     * 
     * @return true si le compte est accessible, false si verrouillé
     */
    public boolean isAccountNonLocked() {
        // Si le compte n'est pas verrouillé, retourne true
        if (!accountLocked) {
            return true;
        }
        
        // Vérifie si 15 minutes se sont écoulées depuis le verrouillage
        if (lockTime != null && LocalDateTime.now().isAfter(lockTime.plusMinutes(15))) {
            // Auto-déverrouillage
            this.accountLocked = false;
            this.failedLoginAttempts = 0;
            this.lockTime = null;
            return true;
        }
        
        // Compte toujours verrouillé
        return false;
    }
    
    /**
     * Incrémente le compteur de tentatives échouées
     * Verrouille le compte si le seuil est atteint
     */
    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
        this.lastFailedLogin = LocalDateTime.now();
        
        // Verrouille le compte après 5 tentatives échouées
        if (this.failedLoginAttempts >= 5) {
            this.accountLocked = true;
            this.lockTime = LocalDateTime.now();
        }
    }
    
    /**
     * Réinitialise les tentatives après une connexion réussie
     */
    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.accountLocked = false;
        this.lockTime = null;
        this.lastLogin = LocalDateTime.now();
    }
}
```

### 2. Service Métier

**Fichier:** `UserService.java`

```java
@Service
public class UserService {
    
    /**
     * Gère une tentative de connexion échouée
     * 
     * @param username Nom d'utilisateur
     * @param ipAddress Adresse IP de la tentative
     * @param userAgent User agent du client
     * @param reason Raison de l'échec
     */
    @Transactional
    public void handleFailedLogin(String username, String ipAddress, 
                                   String userAgent, String reason) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Incrémente le compteur de tentatives
            user.incrementFailedAttempts();
            userRepository.save(user);
            
            // Log l'échec dans les audit logs
            auditService.logLoginFailed(username, ipAddress, userAgent, reason);
            
            // Si le compte vient d'être verrouillé, log aussi
            if (user.getAccountLocked()) {
                auditService.logAccountLocked(username, ipAddress,
                    "Too many failed login attempts (" + user.getFailedLoginAttempts() + ")");
            }
        } else {
            // Même si l'utilisateur n'existe pas, on log pour monitoring
            auditService.logLoginFailed(username, ipAddress, userAgent, "User not found");
        }
    }
    
    /**
     * Gère une connexion réussie
     * Réinitialise les compteurs de sécurité
     */
    @Transactional
    public void handleSuccessfulLogin(User user, String ipAddress, 
                                       String userAgent, String device) {
        // Réinitialise les tentatives échouées
        user.resetFailedAttempts();
        user.setLastLoginIp(ipAddress);
        user.setLastLoginDevice(device);
        userRepository.save(user);
        
        // Log le succès
        auditService.logLoginSuccess(user.getUsername(), ipAddress, userAgent);
    }
    
    /**
     * Vérifie si un compte est verrouillé
     */
    public boolean isAccountLocked(String username) {
        return userRepository.findByUsername(username)
                .map(user -> !user.isAccountNonLocked())
                .orElse(false);
    }
    
    /**
     * Calcule le temps restant avant déverrouillage automatique
     * 
     * @param username Nom d'utilisateur
     * @return Nombre de minutes restantes (0 si non verrouillé)
     */
    public long getRemainingLockTime(String username) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    if (user.getAccountLocked() && user.getLockTime() != null) {
                        LocalDateTime unlockTime = user.getLockTime().plusMinutes(15);
                        LocalDateTime now = LocalDateTime.now();
                        
                        if (now.isBefore(unlockTime)) {
                            return java.time.Duration.between(now, unlockTime).toMinutes();
                        }
                    }
                    return 0L;
                })
                .orElse(0L);
    }
}
```

### 3. Contrôleur

**Fichier:** `AuthController.java`

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody AuthRequest request, 
                                HttpServletRequest httpRequest) {
    String ipAddress = getClientIp(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    
    // 🔒 STEP 1: Vérifier si l'utilisateur existe
    Optional<User> userOpt = userService.findByUsername(request.getUsername());
    
    if (userOpt.isEmpty()) {
        userService.handleFailedLogin(request.getUsername(), ipAddress, 
                                     userAgent, "User not found");
        return ResponseEntity.status(401).body(Map.of(
            "error", "Invalid credentials",
            "message", "Username or password is incorrect"
        ));
    }
    
    User user = userOpt.get();
    
    // 🔒 STEP 2: Vérifier si le compte est verrouillé
    if (!user.isAccountNonLocked()) {
        long remainingMinutes = userService.getRemainingLockTime(request.getUsername());
        return ResponseEntity.status(423).body(Map.of(
            "error", "Account locked",
            "message", "Too many failed login attempts. Account is locked.",
            "remainingLockTimeMinutes", remainingMinutes,
            "tryAgainIn", remainingMinutes + " minutes"
        ));
    }
    
    // 🔒 STEP 3: Vérifier le mot de passe
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        // Mot de passe incorrect → incrémenter les tentatives
        userService.handleFailedLogin(request.getUsername(), ipAddress, 
                                     userAgent, "Invalid password");
        
        // Récupérer l'utilisateur mis à jour
        user = userService.findByUsername(request.getUsername()).get();
        
        // Si le compte vient d'être verrouillé
        if (user.getAccountLocked()) {
            return ResponseEntity.status(423).body(Map.of(
                "error", "Account locked",
                "message", "Too many failed login attempts. Account locked for 15 minutes.",
                "failedAttempts", user.getFailedLoginAttempts()
            ));
        }
        
        // Calculer les tentatives restantes
        int remainingAttempts = 5 - user.getFailedLoginAttempts();
        return ResponseEntity.status(401).body(Map.of(
            "error", "Invalid credentials",
            "message", "Username or password is incorrect",
            "remainingAttempts", remainingAttempts,
            "warning", remainingAttempts <= 2 ? 
                "Account will be locked after " + remainingAttempts + " more failed attempts" : ""
        ));
    }
    
    // 🔒 STEP 4: Connexion réussie → réinitialiser les tentatives
    String device = extractDevice(userAgent);
    userService.handleSuccessfulLogin(user, ipAddress, userAgent, device);
    
    // Générer les tokens...
    // ...
}
```

---

## 🌐 Endpoints et Flux

### POST /auth/login - Flux Complet

#### Scénario 1: Tentative Échouée (1ère-4ème)

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "wrongpassword"
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

**État en Base de Données:**
```sql
SELECT failed_login_attempts, account_locked, lock_time 
FROM users 
WHERE username = 'admin';
```
```
failed_login_attempts: 1
account_locked: false
lock_time: NULL
```

**Traitement:**
1. Mot de passe incorrect détecté
2. `incrementFailedAttempts()` → `failed_login_attempts = 1`
3. `account_locked` reste `false` (< 5 tentatives)
4. Retourne `remainingAttempts: 4`

---

#### Scénario 2: Tentative Échouée (3ème - Avertissement)

**Request:** (même que Scénario 1, 3ème tentative)

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 2,
  "warning": "Account will be locked after 2 more failed attempts"
}
```

**État en Base:**
```
failed_login_attempts: 3
account_locked: false
lock_time: NULL
```

**⚠️ Avertissement activé** car `remainingAttempts <= 2`

---

#### Scénario 3: Verrouillage (5ème Tentative)

**Request:** (même que Scénario 1, 5ème tentative)

**Response (423 Locked):**
```json
{
  "error": "Account locked",
  "message": "Too many failed login attempts. Account locked for 15 minutes.",
  "failedAttempts": 5
}
```

**État en Base:**
```sql
SELECT failed_login_attempts, account_locked, lock_time 
FROM users 
WHERE username = 'admin';
```
```
failed_login_attempts: 5
account_locked: true
lock_time: 2026-01-14 12:30:00
```

**Traitement:**
1. 5ème tentative échouée
2. `incrementFailedAttempts()` → `failed_login_attempts = 5`
3. Condition `>= 5` déclenchée
4. `account_locked = true`
5. `lock_time = now()`
6. Log `ACCOUNT_LOCKED` dans audit logs

---

#### Scénario 4: Tentative avec Compte Verrouillé

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "correctpassword"
}
```

**Response (423 Locked):**
```json
{
  "error": "Account locked",
  "message": "Too many failed login attempts. Account is locked.",
  "remainingLockTimeMinutes": 12,
  "tryAgainIn": "12 minutes"
}
```

**Traitement:**
1. `isAccountNonLocked()` retourne `false`
2. Vérifie si 15 minutes se sont écoulées
3. Si non → Retourne 423 avec temps restant
4. **Même avec le bon mot de passe, l'accès est refusé**

---

#### Scénario 5: Auto-Déverrouillage (Après 15 Minutes)

**État Initial:**
```
account_locked: true
lock_time: 2026-01-14 12:30:00
Current time: 2026-01-14 12:45:01 (15 minutes + 1 seconde)
```

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "correctpassword"
}
```

**Traitement:**
1. `isAccountNonLocked()` est appelé
2. Vérifie: `lock_time + 15 minutes < now?` → **Oui**
3. Auto-déverrouillage:
   - `account_locked = false`
   - `failed_login_attempts = 0`
   - `lock_time = null`
4. Continue avec vérification du mot de passe
5. Si mot de passe correct → Connexion réussie

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**État Final en Base:**
```
failed_login_attempts: 0
account_locked: false
lock_time: NULL
```

---

#### Scénario 6: Connexion Réussie (Réinitialisation)

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "correctpassword"
}
```

**Traitement:**
1. Compte non verrouillé ✅
2. Mot de passe correct ✅
3. `handleSuccessfulLogin()` appelé
4. `resetFailedAttempts()`:
   - `failed_login_attempts = 0`
   - `account_locked = false`
   - `lock_time = null`
   - `last_login = now()`
5. Génère tokens
6. Log `LOGIN_SUCCESS`

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 🚫 Contraintes et Limitations

### Contraintes Configurées

1. **Seuil de Verrouillage:**
   - **Valeur:** 5 tentatives échouées
   - **Code:** `if (this.failedLoginAttempts >= 5)`
   - **Modifiable:** Oui, dans `User.incrementFailedAttempts()`

2. **Durée de Verrouillage:**
   - **Valeur:** 15 minutes
   - **Code:** `lockTime.plusMinutes(15)`
   - **Modifiable:** Oui, dans `User.isAccountNonLocked()`

3. **Seuil d'Avertissement:**
   - **Valeur:** 2 tentatives restantes
   - **Code:** `remainingAttempts <= 2`
   - **Modifiable:** Oui, dans `AuthController.login()`

### Limitations

1. **Pas de Verrouillage Permanent:**
   - Le compte se déverrouille automatiquement après 15 minutes
   - Pour un verrouillage permanent, intervention admin nécessaire

2. **Compteur par Utilisateur:**
   - Chaque utilisateur a son propre compteur
   - Un attaquant peut essayer plusieurs comptes

3. **Pas de Verrouillage par IP:**
   - Le verrouillage est par compte, pas par IP
   - Un attaquant peut essayer depuis différentes IPs

4. **Auto-Déverrouillage en Mémoire:**
   - Le déverrouillage se fait lors de la vérification
   - Si l'utilisateur ne tente pas de se connecter, le statut reste "locked" en base

### Améliorations Possibles

1. **Verrouillage par IP:**
   - Bloquer une IP après X tentatives
   - Nécessite une table supplémentaire

2. **Délai Progressif:**
   - Augmenter le délai après chaque verrouillage
   - Ex: 15 min, 30 min, 1 heure, etc.

3. **CAPTCHA:**
   - Ajouter un CAPTCHA après 3 tentatives
   - Empêche les attaques automatisées

---

## 🧪 Scénarios de Test

### Test Complet: 5 Tentatives → Verrouillage

**Étape 1: Tentative 1**
```http
POST /auth/login
{ "username": "admin", "password": "wrong1" }
→ 401, remainingAttempts: 4
```

**Étape 2: Tentative 2**
```http
POST /auth/login
{ "username": "admin", "password": "wrong2" }
→ 401, remainingAttempts: 3
```

**Étape 3: Tentative 3**
```http
POST /auth/login
{ "username": "admin", "password": "wrong3" }
→ 401, remainingAttempts: 2, warning: "Account will be locked after 2 more failed attempts"
```

**Étape 4: Tentative 4**
```http
POST /auth/login
{ "username": "admin", "password": "wrong4" }
→ 401, remainingAttempts: 1, warning: "Account will be locked after 1 more failed attempts"
```

**Étape 5: Tentative 5 (Verrouillage)**
```http
POST /auth/login
{ "username": "admin", "password": "wrong5" }
→ 423 Locked, failedAttempts: 5
```

**Vérification Base de Données:**
```sql
SELECT failed_login_attempts, account_locked, lock_time 
FROM users WHERE username = 'admin';
```
```
failed_login_attempts: 5
account_locked: true
lock_time: 2026-01-14 12:30:00
```

**Étape 6: Tentative avec Compte Verrouillé**
```http
POST /auth/login
{ "username": "admin", "password": "correct" }
→ 423 Locked, remainingLockTimeMinutes: 15
```

---

## 📊 Impact sur la Sécurité

### Avant Protection

```
❌ Attaquant peut essayer 1000+ mots de passe/seconde
❌ Compte compromis en quelques minutes
❌ Aucune détection
❌ Aucune alerte
```

### Avec Protection

```
✅ Maximum 5 tentatives
✅ Verrouillage automatique
✅ Délai de 15 minutes
✅ Logging complet
✅ Alertes et monitoring
✅ Auto-déverrouillage après délai
```

### Statistiques de Protection

| Métrique | Sans Protection | Avec Protection |
|----------|----------------|-----------------|
| Tentatives max | Illimité | 5 |
| Temps de compromission | Minutes | Impossible |
| Détection | Non | Oui (logs) |
| Auto-déverrouillage | N/A | 15 minutes |

---

## 🎯 Résumé

### Concept
La protection brute-force limite le nombre de tentatives de connexion échouées et verrouille automatiquement le compte après un seuil défini.

### Rôle dans l'Architecture
- **Modèle:** `User` - Stocke les compteurs et statut
- **Service:** `UserService` - Gère la logique métier
- **Contrôleur:** `AuthController` - Vérifie avant chaque tentative
- **Audit:** `SecurityAuditService` - Log tous les événements

### Implémentation
- **Seuil:** 5 tentatives échouées
- **Durée:** 15 minutes de verrouillage
- **Auto-déverrouillage:** Automatique après délai
- **Réinitialisation:** Après connexion réussie

### Endpoints
- `POST /auth/login` - Vérifie et applique la protection

### Sécurité
- ✅ Limite les tentatives
- ✅ Verrouillage automatique
- ✅ Délai empêche les attaques automatisées
- ✅ Logging complet pour monitoring

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
