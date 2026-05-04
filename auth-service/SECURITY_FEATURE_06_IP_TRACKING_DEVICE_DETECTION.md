# 🌐 Fonctionnalité de Sécurité #6: IP Tracking & Device Detection

## 📚 Table des Matières

1. [Concept et Théorie](#concept-et-théorie)
2. [Pourquoi IP Tracking & Device Detection?](#pourquoi-ip-tracking--device-detection)
3. [Rôle dans l'Architecture](#rôle-dans-larchitecture)
4. [Implémentation Spring Boot](#implémentation-spring-boot)
5. [Endpoints et Utilisation](#endpoints-et-utilisation)
6. [Contraintes et Limitations](#contraintes-et-limitations)
7. [Détection d'Anomalies](#détection-danomalies)

---

## 🎓 Concept et Théorie

### Qu'est-ce que l'IP Tracking?

L'**IP tracking** est l'enregistrement de l'adresse IP du client pour chaque événement de sécurité (connexion, tentative, etc.). Cela permet de:
- Identifier la source géographique des requêtes
- Détecter les changements d'IP suspects
- Bloquer les IPs malveillantes
- Analyser les patterns d'accès

### Qu'est-ce que la Device Detection?

La **device detection** (détection d'appareil) identifie le type d'appareil utilisé par le client (Desktop, Mobile, Tablet) en analysant le header `User-Agent`. Cela permet de:
- Identifier les nouveaux appareils
- Détecter les accès suspects depuis nouveaux appareils
- Améliorer l'expérience utilisateur
- Monitoring des sessions multi-appareils

### Headers HTTP Utilisés

**1. X-Forwarded-For:**
```
X-Forwarded-For: 192.168.1.100, 10.0.0.1
```
- Utilisé par les proxies et load balancers
- Contient l'IP originale du client
- Peut contenir plusieurs IPs (chaîne de proxies)

**2. X-Real-IP:**
```
X-Real-IP: 192.168.1.100
```
- Utilisé par les reverse proxies (Nginx, etc.)
- Contient l'IP réelle du client
- Une seule IP

**3. User-Agent:**
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
```
- Contient des informations sur le navigateur et l'OS
- Utilisé pour la détection d'appareil

**4. Remote Address:**
```
request.getRemoteAddr() → 127.0.0.1
```
- IP de connexion directe au serveur
- Utilisé en dernier recours si les headers ne sont pas disponibles

---

## 🤔 Pourquoi IP Tracking & Device Detection?

### Cas d'Usage

**1. Détection de Session Hijacking:**
```
Scénario: Utilisateur se connecte depuis Paris (IP: 192.168.1.100)
          Puis connexion depuis Moscou (IP: 203.0.113.50) 5 minutes après
→ Alerte: Changement d'IP suspect
→ Action: Demander ré-authentification ou bloquer
```

**2. Détection de Nouveaux Appareils:**
```
Scénario: Utilisateur se connecte toujours depuis Desktop Windows
          Puis connexion depuis Mobile Android
→ Alerte: Nouvel appareil détecté
→ Action: Envoyer notification email ou SMS
```

**3. Blocage d'IPs Malveillantes:**
```
Scénario: 100 tentatives de connexion échouées depuis IP 192.168.1.200
→ Action: Bloquer l'IP automatiquement
→ Résultat: Plus de tentatives depuis cette IP
```

**4. Analyse Géographique:**
```
Scénario: Tous les accès depuis pays X
→ Analyse: Patterns d'utilisation par région
→ Optimisation: CDN ou serveurs régionaux
```

---

## 🏗️ Rôle dans l'Architecture

### Position dans l'Architecture

```
┌─────────────────────────────────────────┐
│         CLIENT                          │
│  - Envoie requête HTTP                  │
│  - Headers: X-Forwarded-For, User-Agent│
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         PROXY/LOAD BALANCER (Optionnel) │
│  - Ajoute X-Forwarded-For                │
│  - Ajoute X-Real-IP                      │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│         (AuthController)                 │
│  - getClientIp() → Extrait IP             │
│  - extractDevice() → Détecte appareil     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         BUSINESS LAYER                   │
│         (UserService)                    │
│  - Stocke IP et device dans User         │
│  - Stocke IP dans RefreshToken           │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATA LAYER                       │
│         (Database)                       │
│  - users.last_login_ip                   │
│  - users.last_login_device               │
│  - refresh_tokens.ip_address             │
│  - security_audit_logs.ip_address        │
└─────────────────────────────────────────┘
```

### Flux de Tracking

**1. Connexion:**
```
Client → POST /auth/login
  Headers:
    X-Forwarded-For: 192.168.1.100
    User-Agent: Mozilla/5.0 (Windows NT 10.0...)
  ↓
AuthController.getClientIp() extrait IP
  → Priorité: X-Forwarded-For > X-Real-IP > RemoteAddr
  → Résultat: 192.168.1.100
  ↓
AuthController.extractDevice() analyse User-Agent
  → Contient "Windows" → Desktop
  → Résultat: "Desktop"
  ↓
UserService.handleSuccessfulLogin() sauvegarde
  → user.last_login_ip = "192.168.1.100"
  → user.last_login_device = "Desktop"
  ↓
RefreshTokenService.createRefreshToken() sauvegarde
  → refresh_token.ip_address = "192.168.1.100"
  → refresh_token.device_type = "Desktop"
```

**2. Audit Logging:**
```
Tous les événements loggent l'IP:
  → security_audit_logs.ip_address = "192.168.1.100"
```

---

## 💻 Implémentation Spring Boot

### 1. Extraction d'IP

**Fichier:** `AuthController.java`

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    /**
     * Extrait l'adresse IP réelle du client
     * Gère les proxies et load balancers
     * 
     * @param request HttpServletRequest
     * @return Adresse IP du client
     */
    private String getClientIp(HttpServletRequest request) {
        // Priorité 1: X-Forwarded-For (pour proxies/load balancers)
        String ip = getHeaderCaseInsensitive(request, "X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // Priorité 2: X-Real-IP (pour reverse proxies)
            ip = getHeaderCaseInsensitive(request, "X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // Priorité 3: Remote Address (connexion directe)
            ip = request.getRemoteAddr();
        }
        
        // Si plusieurs IPs (chaîne de proxies), prendre la première
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * Récupère un header de manière case-insensitive
     * (Certains proxies peuvent envoyer X-Forwarded-For ou x-forwarded-for)
     */
    private String getHeaderCaseInsensitive(HttpServletRequest request, String headerName) {
        // Parcourir tous les headers pour trouver celui qui correspond (case-insensitive)
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            if (header.equalsIgnoreCase(headerName)) {
                return request.getHeader(header);
            }
        }
        return null;
    }
}
```

**Explication:**
- **Priorité:** X-Forwarded-For > X-Real-IP > RemoteAddr
- **Gestion des proxies:** Prend la première IP si plusieurs
- **Case-insensitive:** Gère les variations de casse

### 2. Détection d'Appareil

**Fichier:** `AuthController.java`

```java
/**
 * Détecte le type d'appareil depuis le User-Agent
 * 
 * @param userAgent Header User-Agent
 * @return "Mobile", "Tablet", ou "Desktop"
 */
private String extractDevice(String userAgent) {
    if (userAgent == null) {
        return "Unknown";
    }
    
    // Convertir en minuscules pour comparaison
    String userAgentLower = userAgent.toLowerCase();
    
    // Détection Mobile
    if (userAgentLower.contains("mobile") 
        || userAgentLower.contains("android") 
        || userAgentLower.contains("iphone")
        || userAgentLower.contains("ipod")) {
        return "Mobile";
    }
    
    // Détection Tablet
    if (userAgentLower.contains("tablet") 
        || userAgentLower.contains("ipad")) {
        return "Tablet";
    }
    
    // Par défaut: Desktop
    return "Desktop";
}
```

**Exemples de User-Agents:**

| User-Agent | Détection |
|------------|-----------|
| `Mozilla/5.0 (Windows NT 10.0; Win64; x64)...` | Desktop |
| `Mozilla/5.0 (iPhone; CPU iPhone OS 17_0...` | Mobile |
| `Mozilla/5.0 (iPad; CPU OS 17_0...` | Tablet |
| `Mozilla/5.0 (Linux; Android 13...` | Mobile |

### 3. Stockage dans le Modèle User

**Fichier:** `User.java`

```java
@Entity
@Table(name = "users")
public class User {
    
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;  // Dernière IP de connexion réussie
    
    @Column(name = "last_login_device", length = 50)
    private String lastLoginDevice;  // Dernier appareil utilisé (Desktop/Mobile/Tablet)
}
```

### 4. Stockage dans RefreshToken

**Fichier:** `RefreshToken.java`

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // IP de création du token
    
    @Column(name = "device_type", length = 50)
    private String deviceType;  // Type d'appareil (Desktop/Mobile/Tablet)
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;  // User agent complet
}
```

### 5. Utilisation dans AuthController

**Fichier:** `AuthController.java`

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
    // 🔒 STEP 1: Extraire IP et User-Agent
    String ipAddress = getClientIp(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    
    // ... vérification credentials ...
    
    // 🔒 STEP 2: Détecter le type d'appareil
    String device = extractDevice(userAgent);
    
    // 🔒 STEP 3: Sauvegarder IP et device
    userService.handleSuccessfulLogin(user, ipAddress, userAgent, device);
    // → Met à jour user.last_login_ip et user.last_login_device
    
    // 🔒 STEP 4: Créer refresh token avec IP et device
    RefreshToken refreshToken = refreshTokenService.createRefreshToken(
        user, ipAddress, userAgent, device
    );
    // → Stocke ip_address, device_type, user_agent dans refresh_token
    
    // 🔒 STEP 5: Logger avec IP
    auditService.logLoginSuccess(user.getUsername(), ipAddress, userAgent);
    // → Stocke ip_address dans security_audit_logs
    
    // ...
}
```

---

## 🌐 Endpoints et Utilisation

### 1. POST /auth/login - Tracking Automatique

**Description:** IP et device sont automatiquement trackés lors de la connexion

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json
X-Forwarded-For: 192.168.1.100
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36

{
  "username": "admin",
  "password": "password123"
}
```

**Traitement Interne:**
1. `getClientIp()` extrait: `192.168.1.100`
2. `extractDevice()` détecte: `Desktop`
3. Sauvegarde dans `users`:
   - `last_login_ip = "192.168.1.100"`
   - `last_login_device = "Desktop"`
4. Sauvegarde dans `refresh_tokens`:
   - `ip_address = "192.168.1.100"`
   - `device_type = "Desktop"`
   - `user_agent = "Mozilla/5.0..."`
5. Sauvegarde dans `security_audit_logs`:
   - `ip_address = "192.168.1.100"`

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Vérification en Base:**
```sql
SELECT last_login_ip, last_login_device 
FROM users 
WHERE username = 'admin';
```
```
last_login_ip: 192.168.1.100
last_login_device: Desktop
```

---

### 2. GET /auth/sessions - Voir les Sessions avec IP et Device

**Description:** Liste toutes les sessions actives avec IP et device

**Request:**
```http
GET http://localhost:8081/auth/sessions
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Response (200 OK):**
```json
[
  {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "deviceType": "Desktop",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0...",
    "createdAt": "2026-01-14T12:30:00",
    "expiryDate": "2026-02-14T12:30:00",
    "revoked": false
  },
  {
    "token": "660e8400-e29b-41d4-a716-446655440001",
    "deviceType": "Mobile",
    "ipAddress": "192.168.1.101",
    "userAgent": "Mozilla/5.0 (iPhone...",
    "createdAt": "2026-01-14T13:00:00",
    "expiryDate": "2026-02-14T13:00:00",
    "revoked": false
  }
]
```

**Utilisation:**
- Voir tous les appareils connectés
- Identifier les sessions suspectes (IPs différentes)
- Déclencher un logout-all si nécessaire

---

## 🚫 Contraintes et Limitations

### Contraintes Techniques

1. **IP Peut Être Falsifiée:**
   - Les headers `X-Forwarded-For` peuvent être modifiés par le client
   - **Solution:** Faire confiance uniquement aux proxies de confiance

2. **IPv6 vs IPv4:**
   - Support des deux formats
   - Colonne `ip_address` doit être `VARCHAR(45)` (IPv6 max)

3. **User-Agent Peut Être Falsifié:**
   - Le client peut modifier le User-Agent
   - **Solution:** Utiliser pour information, pas pour sécurité critique

4. **Proxies et NAT:**
   - Plusieurs clients peuvent avoir la même IP (NAT)
   - **Solution:** Combiner IP + User-Agent pour identification

### Limitations

1. **Pas de Géolocalisation:**
   - L'IP ne donne pas directement la localisation
   - **Solution:** Utiliser un service de géolocalisation (MaxMind, etc.)

2. **Pas de Détection de VPN/Proxy:**
   - Impossible de détecter si l'IP est un VPN
   - **Solution:** Utiliser un service de détection (IPQualityScore, etc.)

3. **User-Agent Parsing Simple:**
   - Détection basique (Mobile/Tablet/Desktop)
   - **Solution:** Utiliser une bibliothèque (User-Agent-Utils, etc.)

### Améliorations Possibles

1. **Géolocalisation:**
   - Service MaxMind GeoIP2
   - Enregistrer pays, ville, timezone

2. **Détection de VPN/Proxy:**
   - Service IPQualityScore
   - Bloquer les IPs suspectes

3. **Fingerprinting Avancé:**
   - Combiner IP + User-Agent + Screen Resolution
   - Détection plus précise des appareils

4. **Alertes sur Changements:**
   - Email/SMS si nouvelle IP ou appareil
   - Confirmation avant autorisation

---

## 🔍 Détection d'Anomalies

### Requêtes SQL pour Détection

#### 1. Changements d'IP Suspects

```sql
-- Utilisateurs avec plusieurs IPs différentes récemment
SELECT 
    u.username,
    COUNT(DISTINCT rt.ip_address) as unique_ips,
    STRING_AGG(DISTINCT rt.ip_address, ', ') as ip_addresses
FROM refresh_tokens rt
JOIN users u ON rt.user_id = u.id
WHERE rt.revoked = false
  AND rt.created_at > NOW() - INTERVAL '7 days'
GROUP BY u.username
HAVING COUNT(DISTINCT rt.ip_address) > 3  -- Plus de 3 IPs différentes
ORDER BY unique_ips DESC;
```

#### 2. Sessions depuis IPs Suspectes

```sql
-- IPs avec beaucoup de tentatives échouées
SELECT 
    sal.ip_address,
    COUNT(*) as failed_attempts,
    COUNT(DISTINCT sal.username) as unique_users
FROM security_audit_logs sal
WHERE sal.event_type = 'LOGIN_FAILED'
  AND sal.timestamp > NOW() - INTERVAL '1 hour'
GROUP BY sal.ip_address
HAVING COUNT(*) > 10  -- Plus de 10 tentatives en 1 heure
ORDER BY failed_attempts DESC;
```

#### 3. Nouveaux Appareils

```sql
-- Utilisateurs avec nouveaux types d'appareils
SELECT 
    u.username,
    u.last_login_device as previous_device,
    rt.device_type as new_device,
    rt.ip_address,
    rt.created_at
FROM refresh_tokens rt
JOIN users u ON rt.user_id = u.id
WHERE rt.revoked = false
  AND rt.device_type != u.last_login_device
  AND rt.created_at > NOW() - INTERVAL '24 hours'
ORDER BY rt.created_at DESC;
```

#### 4. Patterns d'Accès Suspects

```sql
-- Connexions depuis IPs différentes en peu de temps
SELECT 
    u.username,
    COUNT(DISTINCT rt.ip_address) as unique_ips,
    MIN(rt.created_at) as first_login,
    MAX(rt.created_at) as last_login,
    EXTRACT(EPOCH FROM (MAX(rt.created_at) - MIN(rt.created_at))) / 60 as minutes_span
FROM refresh_tokens rt
JOIN users u ON rt.user_id = u.id
WHERE rt.revoked = false
  AND rt.created_at > NOW() - INTERVAL '1 hour'
GROUP BY u.username
HAVING COUNT(DISTINCT rt.ip_address) > 2  -- Plus de 2 IPs en 1 heure
   AND EXTRACT(EPOCH FROM (MAX(rt.created_at) - MIN(rt.created_at))) / 60 < 60  -- Moins de 60 minutes
ORDER BY unique_ips DESC;
```

---

## 🎯 Résumé

### Concept
IP tracking et device detection enregistrent l'adresse IP et le type d'appareil pour chaque événement de sécurité, permettant la détection d'anomalies et le monitoring.

### Rôle dans l'Architecture
- **Contrôleur:** `AuthController` - Extraction IP et détection device
- **Modèle:** `User`, `RefreshToken`, `SecurityAuditLog` - Stockage
- **Service:** Utilisé par tous les services de sécurité

### Implémentation
- **Extraction IP:** Priorité X-Forwarded-For > X-Real-IP > RemoteAddr
- **Détection Device:** Analyse du User-Agent (Mobile/Tablet/Desktop)
- **Stockage:** Dans `users`, `refresh_tokens`, `security_audit_logs`

### Endpoints
- `POST /auth/login` - Tracking automatique
- `GET /auth/sessions` - Voir les sessions avec IP et device

### Sécurité
- ✅ Détection de changements d'IP suspects
- ✅ Identification de nouveaux appareils
- ✅ Tracking complet pour analyse forensique
- ✅ Monitoring des sessions multi-appareils

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
