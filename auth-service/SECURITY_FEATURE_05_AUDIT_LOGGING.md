# 📝 Fonctionnalité de Sécurité #5: Audit Logging

## 📚 Table des Matières

1. [Concept et Théorie](#concept-et-théorie)
2. [Pourquoi l'Audit Logging?](#pourquoi-laudit-logging)
3. [Rôle dans l'Architecture](#rôle-dans-larchitecture)
4. [Implémentation Spring Boot](#implémentation-spring-boot)
5. [Types d'Événements](#types-dévénements)
6. [Endpoints et Requêtes](#endpoints-et-requêtes)
7. [Contraintes et Limitations](#contraintes-et-limitations)

---

## 🎓 Concept et Théorie

### Qu'est-ce que l'Audit Logging?

L'**audit logging** (journalisation d'audit) est l'enregistrement systématique de tous les événements de sécurité dans un système pour permettre la traçabilité, la détection d'anomalies, et l'analyse forensique.

**Caractéristiques:**
- **Immuable:** Les logs ne doivent pas être modifiables
- **Complet:** Tous les événements importants sont enregistrés
- **Traçable:** Chaque événement contient qui, quoi, quand, où
- **Persistant:** Stockage en base de données pour analyse

### Pourquoi C'est Essentiel?

**Sécurité:**
- Détecter les tentatives d'intrusion
- Identifier les comportements suspects
- Analyser les incidents après coup

**Conformité:**
- Répondre aux exigences réglementaires (GDPR, HIPAA, etc.)
- Fournir des preuves en cas d'audit
- Démontrer la diligence raisonnable

**Opérationnel:**
- Déboguer les problèmes
- Comprendre les patterns d'utilisation
- Optimiser les performances

---

## 🤔 Pourquoi l'Audit Logging?

### Scénarios d'Utilisation

**1. Détection d'Intrusion:**
```
Log: LOGIN_FAILED depuis IP 192.168.1.100, 5 tentatives en 2 minutes
→ Alerte: Possible attaque brute-force
→ Action: Verrouiller l'IP ou le compte
```

**2. Analyse Forensique:**
```
Incident: Compte compromis
→ Analyse des logs: Qui s'est connecté? Quand? Depuis où?
→ Identification de la source de compromission
```

**3. Conformité:**
```
Audit: Qui a accédé aux données sensibles?
→ Requête SQL sur security_audit_logs
→ Rapport complet des accès
```

**4. Monitoring en Temps Réel:**
```
Dashboard: Surveiller les événements en temps réel
→ Alertes automatiques sur patterns suspects
→ Réaction rapide aux incidents
```

---

## 🏗️ Rôle dans l'Architecture

### Position dans l'Architecture

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│         (AuthController)                 │
│  - Tous les endpoints loggent            │
│  - Appelle SecurityAuditService           │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         BUSINESS LAYER                   │
│         (SecurityAuditService)            │
│  - logEvent() → Log générique             │
│  - logLoginSuccess() → Log succès         │
│  - logLoginFailed() → Log échec           │
│  - logAccountLocked() → Log verrouillage  │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATA LAYER                       │
│         (SecurityAuditLogRepository)      │
│  - save() → Sauvegarde en base           │
│  - findByUsername() → Recherche          │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATABASE                         │
│         (security_audit_logs table)      │
│  - Tous les événements stockés            │
│  - Index sur username, timestamp          │
└─────────────────────────────────────────┘
```

### Flux de Logging

**Exemple: Connexion Réussie**
```
1. User → POST /auth/login { username, password }
   ↓
2. AuthController.login() vérifie credentials
   ↓
3. Credentials valides → Connexion réussie
   ↓
4. SecurityAuditService.logLoginSuccess(username, ip, userAgent)
   ↓
5. Création de SecurityAuditLog:
   - username: "admin"
   - event_type: "LOGIN_SUCCESS"
   - ip_address: "192.168.1.100"
   - user_agent: "Mozilla/5.0..."
   - success: true
   - timestamp: now()
   ↓
6. Sauvegarde en base de données
   ↓
7. Log aussi dans application logs (Slf4j)
```

---

## 💻 Implémentation Spring Boot

### 1. Modèle de Données

**Fichier:** `SecurityAuditLog.java`

```java
@Entity
@Table(name = "security_audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username")
    private String username;  // Utilisateur concerné

    @Column(name = "event_type", nullable = false)
    private String eventType;  // Type d'événement (LOGIN_SUCCESS, etc.)

    @Column(name = "ip_address")
    private String ipAddress;  // Adresse IP du client

    @Column(name = "user_agent")
    private String userAgent;  // User agent du client

    @Column(name = "details", length = 1000)
    private String details;  // Détails supplémentaires

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;  // Date/heure de l'événement

    @Column(name = "success")
    private Boolean success;  // Succès ou échec

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();  // Timestamp automatique
    }

    // Constantes pour les types d'événements
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    public static final String REGISTRATION = "REGISTRATION";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String LOGOUT = "LOGOUT";
    public static final String LOGOUT_ALL = "LOGOUT_ALL";
    public static final String MFA_ENABLED = "MFA_ENABLED";
    public static final String MFA_DISABLED = "MFA_DISABLED";
    public static final String MFA_VERIFICATION_SUCCESS = "MFA_VERIFICATION_SUCCESS";
    public static final String MFA_VERIFICATION_FAILED = "MFA_VERIFICATION_FAILED";
}
```

### 2. Repository

**Fichier:** `SecurityAuditLogRepository.java`

```java
@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {
    
    /**
     * Trouve tous les logs d'un utilisateur, triés par date décroissante
     */
    List<SecurityAuditLog> findByUsernameOrderByTimestampDesc(String username);
    
    /**
     * Trouve les logs d'un type spécifique après une date
     */
    List<SecurityAuditLog> findByUsernameAndEventTypeAndTimestampAfter(
        String username, 
        String eventType, 
        LocalDateTime after
    );
    
    /**
     * Trouve les logs d'une IP spécifique après une date
     */
    List<SecurityAuditLog> findByIpAddressAndEventTypeAndTimestampAfter(
        String ipAddress, 
        String eventType, 
        LocalDateTime after
    );
    
    /**
     * Trouve tous les logs après une date
     */
    List<SecurityAuditLog> findByTimestampAfter(LocalDateTime after);
}
```

### 3. Service Métier

**Fichier:** `SecurityAuditService.java`

```java
@Service
@Slf4j
public class SecurityAuditService {

    private final SecurityAuditLogRepository auditLogRepository;

    /**
     * Log générique d'un événement de sécurité
     */
    @Transactional
    public void logEvent(String username, String eventType, String ipAddress,
                         String userAgent, String details, boolean success) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .username(username)
                .eventType(eventType)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .details(details)
                .success(success)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);

        // Log aussi dans les application logs (Slf4j)
        log.info("Security Event: {} | User: {} | IP: {} | Success: {}",
                eventType, username, ipAddress, success);
    }

    /**
     * Log une connexion réussie
     */
    public void logLoginSuccess(String username, String ipAddress, String userAgent) {
        logEvent(username, SecurityAuditLog.LOGIN_SUCCESS, ipAddress, userAgent,
                "User logged in successfully", true);
    }

    /**
     * Log une connexion échouée
     */
    public void logLoginFailed(String username, String ipAddress, String userAgent, String reason) {
        logEvent(username, SecurityAuditLog.LOGIN_FAILED, ipAddress, userAgent,
                "Login failed: " + reason, false);
    }

    /**
     * Log un verrouillage de compte
     */
    public void logAccountLocked(String username, String ipAddress, String reason) {
        logEvent(username, SecurityAuditLog.ACCOUNT_LOCKED, ipAddress, null,
                "Account locked: " + reason, false);
    }

    /**
     * Log un déverrouillage de compte
     */
    public void logAccountUnlocked(String username) {
        logEvent(username, SecurityAuditLog.ACCOUNT_UNLOCKED, null, null,
                "Account automatically unlocked", true);
    }

    /**
     * Log une inscription
     */
    public void logRegistration(String username, String ipAddress) {
        logEvent(username, SecurityAuditLog.REGISTRATION, ipAddress, null,
                "New user registered", true);
    }

    /**
     * Récupère les tentatives de connexion échouées récentes d'un utilisateur
     */
    public List<SecurityAuditLog> getRecentFailedAttempts(String username, int minutes) {
        LocalDateTime after = LocalDateTime.now().minusMinutes(minutes);
        return auditLogRepository.findByUsernameAndEventTypeAndTimestampAfter(
                username, SecurityAuditLog.LOGIN_FAILED, after
        );
    }

    /**
     * Récupère les tentatives échouées depuis une IP
     */
    public List<SecurityAuditLog> getRecentFailedAttemptsFromIp(String ipAddress, int minutes) {
        LocalDateTime after = LocalDateTime.now().minusMinutes(minutes);
        return auditLogRepository.findByIpAddressAndEventTypeAndTimestampAfter(
                ipAddress, SecurityAuditLog.LOGIN_FAILED, after
        );
    }

    /**
     * Récupère l'historique de connexion d'un utilisateur
     */
    public List<SecurityAuditLog> getUserLoginHistory(String username) {
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username);
    }
}
```

### 4. Utilisation dans AuthController

**Fichier:** `AuthController.java`

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final SecurityAuditService auditService;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, ...) {
        // ... vérification credentials ...
        
        if (passwordCorrect) {
            // ✅ Connexion réussie
            auditService.logLoginSuccess(user.getUsername(), ipAddress, userAgent);
            // ...
        } else {
            // ❌ Connexion échouée
            auditService.logLoginFailed(request.getUsername(), ipAddress, userAgent, "Invalid password");
            // ...
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, ...) {
        // ... création utilisateur ...
        
        // ✅ Inscription réussie
        auditService.logRegistration(created.getUsername(), ipAddress);
        // ...
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request, ...) {
        // ... révocation token ...
        
        // ✅ Déconnexion
        auditService.logEvent(username, SecurityAuditLog.LOGOUT, ipAddress, userAgent,
                "User logged out successfully", true);
        // ...
    }
}
```

---

## 📋 Types d'Événements

### Événements d'Authentification

| Type | Description | Success | Exemple |
|------|-------------|---------|---------|
| `LOGIN_SUCCESS` | Connexion réussie | ✅ true | User "admin" s'est connecté |
| `LOGIN_FAILED` | Connexion échouée | ❌ false | Mot de passe incorrect |
| `REGISTRATION` | Inscription | ✅ true | Nouvel utilisateur créé |

### Événements de Compte

| Type | Description | Success | Exemple |
|------|-------------|---------|---------|
| `ACCOUNT_LOCKED` | Compte verrouillé | ❌ false | 5 tentatives échouées |
| `ACCOUNT_UNLOCKED` | Compte déverrouillé | ✅ true | Auto-déverrouillage après 15 min |

### Événements de Tokens

| Type | Description | Success | Exemple |
|------|-------------|---------|---------|
| `TOKEN_REFRESH` | Renouvellement de token | ✅ true | Access token renouvelé |
| `LOGOUT` | Déconnexion | ✅ true | Refresh token révoqué |
| `LOGOUT_ALL` | Déconnexion de tous les appareils | ✅ true | Tous les tokens révoqués |

### Événements MFA

| Type | Description | Success | Exemple |
|------|-------------|---------|---------|
| `MFA_ENABLED` | MFA activé | ✅ true | TOTP configuré |
| `MFA_DISABLED` | MFA désactivé | ✅ true | MFA retiré |
| `MFA_VERIFICATION_SUCCESS` | Vérification MFA réussie | ✅ true | Code TOTP valide |
| `MFA_VERIFICATION_FAILED` | Vérification MFA échouée | ❌ false | Code TOTP invalide |

---

## 🌐 Endpoints et Requêtes

### 1. GET /auth/login-history/{username} - Historique de Connexion

**Description:** Récupère l'historique complet de connexion d'un utilisateur

**Request:**
```http
GET http://localhost:8081/auth/login-history/admin
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "username": "admin",
    "eventType": "LOGIN_SUCCESS",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...",
    "details": "User logged in successfully",
    "timestamp": "2026-01-14T12:30:00",
    "success": true
  },
  {
    "id": 2,
    "username": "admin",
    "eventType": "LOGIN_FAILED",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "details": "Login failed: Invalid password",
    "timestamp": "2026-01-14T12:29:00",
    "success": false
  },
  {
    "id": 3,
    "username": "admin",
    "eventType": "ACCOUNT_LOCKED",
    "ipAddress": "192.168.1.100",
    "userAgent": null,
    "details": "Account locked: Too many failed login attempts (5)",
    "timestamp": "2026-01-14T12:25:00",
    "success": false
  }
]
```

**Traitement Interne:**
1. `SecurityAuditService.getUserLoginHistory(username)` appelé
2. Requête SQL: `SELECT * FROM security_audit_logs WHERE username = ? ORDER BY timestamp DESC`
3. Retourne tous les événements de l'utilisateur

---

### Requêtes SQL Directes

#### Voir Tous les Événements Récents

```sql
SELECT 
    id,
    username,
    event_type,
    ip_address,
    success,
    timestamp,
    details
FROM security_audit_logs
ORDER BY timestamp DESC
LIMIT 100;
```

#### Tentatives de Connexion Échouées (Dernières 24h)

```sql
SELECT 
    username,
    ip_address,
    COUNT(*) as failed_attempts,
    MAX(timestamp) as last_attempt
FROM security_audit_logs
WHERE event_type = 'LOGIN_FAILED'
  AND timestamp > NOW() - INTERVAL '24 hours'
GROUP BY username, ip_address
ORDER BY failed_attempts DESC;
```

#### Comptes Verrouillés

```sql
SELECT 
    username,
    ip_address,
    timestamp,
    details
FROM security_audit_logs
WHERE event_type = 'ACCOUNT_LOCKED'
  AND timestamp > NOW() - INTERVAL '7 days'
ORDER BY timestamp DESC;
```

#### Sessions Actives par Utilisateur

```sql
SELECT 
    username,
    COUNT(*) as active_sessions,
    MAX(timestamp) as last_activity
FROM security_audit_logs
WHERE event_type = 'LOGIN_SUCCESS'
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY username
ORDER BY active_sessions DESC;
```

#### Détection d'Anomalies: IPs Suspectes

```sql
SELECT 
    ip_address,
    COUNT(DISTINCT username) as unique_users,
    COUNT(*) as total_attempts,
    SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failed_attempts
FROM security_audit_logs
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY ip_address
HAVING COUNT(*) > 10  -- Plus de 10 tentatives en 1 heure
ORDER BY total_attempts DESC;
```

---

## 🚫 Contraintes et Limitations

### Contraintes de Stockage

1. **Taille de la Table:**
   - Les logs s'accumulent indéfiniment
   - Nécessite un archivage ou nettoyage périodique
   - **Recommandation:** Conserver 90 jours, archiver le reste

2. **Performance:**
   - Beaucoup de logs = requêtes lentes
   - **Solution:** Index sur `username`, `timestamp`, `event_type`

3. **Espace Disque:**
   - Chaque log ~200-500 bytes
   - 1 million de logs ≈ 200-500 MB
   - **Solution:** Rotation et archivage

### Limitations

1. **Pas de Modification:**
   - Les logs sont immuables (bon pour sécurité)
   - Impossible de corriger une erreur après écriture

2. **Pas de Suppression Automatique:**
   - Nécessite un job de nettoyage manuel
   - **Solution:** Job Spring qui archive/supprime les anciens logs

3. **Pas de Chiffrement:**
   - Les logs sont en clair en base
   - **Solution:** Chiffrer les champs sensibles si nécessaire

### Améliorations Possibles

1. **Archivage Automatique:**
   - Job qui archive les logs > 90 jours
   - Stockage dans un système d'archivage (S3, etc.)

2. **Alertes Automatiques:**
   - Détecter les patterns suspects
   - Envoyer des notifications (email, Slack, etc.)

3. **Dashboard de Monitoring:**
   - Visualisation en temps réel
   - Graphiques et statistiques

4. **Chiffrement:**
   - Chiffrer les champs sensibles (IP, user agent)
   - Clé de chiffrement séparée

---

## 🔍 Analyse et Monitoring

### Détection d'Attaques Brute-Force

**Requête SQL:**
```sql
SELECT 
    username,
    ip_address,
    COUNT(*) as attempts,
    MIN(timestamp) as first_attempt,
    MAX(timestamp) as last_attempt
FROM security_audit_logs
WHERE event_type = 'LOGIN_FAILED'
  AND timestamp > NOW() - INTERVAL '15 minutes'
GROUP BY username, ip_address
HAVING COUNT(*) >= 5  -- 5 tentatives ou plus
ORDER BY attempts DESC;
```

**Action:** Verrouiller les comptes/IPs suspects

### Analyse de Comportement

**Requête SQL:**
```sql
SELECT 
    username,
    COUNT(DISTINCT ip_address) as unique_ips,
    COUNT(DISTINCT DATE(timestamp)) as active_days,
    COUNT(*) as total_logins
FROM security_audit_logs
WHERE event_type = 'LOGIN_SUCCESS'
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY username
HAVING COUNT(DISTINCT ip_address) > 5;  -- Plus de 5 IPs différentes
```

**Action:** Identifier les comptes avec comportement suspect

### Conformité et Rapports

**Rapport d'Accès (GDPR):**
```sql
SELECT 
    username,
    event_type,
    ip_address,
    timestamp
FROM security_audit_logs
WHERE username = 'user123'
  AND timestamp > '2026-01-01'
ORDER BY timestamp DESC;
```

**Rapport de Sécurité (Mensuel):**
```sql
SELECT 
    event_type,
    COUNT(*) as count,
    SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failures
FROM security_audit_logs
WHERE timestamp > NOW() - INTERVAL '30 days'
GROUP BY event_type
ORDER BY count DESC;
```

---

## 🎯 Résumé

### Concept
L'audit logging enregistre tous les événements de sécurité pour permettre la traçabilité, la détection d'anomalies, et l'analyse forensique.

### Rôle dans l'Architecture
- **Modèle:** `SecurityAuditLog` - Structure des logs
- **Service:** `SecurityAuditService` - Logging et recherche
- **Contrôleur:** `AuthController` - Appelle le service à chaque événement
- **Base de données:** Table `security_audit_logs`

### Implémentation
- **Stockage:** Base de données PostgreSQL
- **Types:** 11 types d'événements différents
- **Métadonnées:** IP, user agent, timestamp, détails
- **Double logging:** Base de données + application logs (Slf4j)

### Endpoints
- `GET /auth/login-history/{username}` - Historique d'un utilisateur

### Sécurité
- ✅ Traçabilité complète
- ✅ Détection d'anomalies
- ✅ Conformité réglementaire
- ✅ Analyse forensique

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
