# 🔐 Fonctionnalité de Sécurité #1: Hashage Argon2

## 📚 Table des Matières

1. [Concept et Théorie](#concept-et-théorie)
2. [Pourquoi Argon2?](#pourquoi-argon2)
3. [Rôle dans l'Architecture](#rôle-dans-larchitecture)
4. [Implémentation Spring Boot](#implémentation-spring-boot)
5. [Endpoints Utilisant Argon2](#endpoints-utilisant-argon2)
6. [Contraintes et Limitations](#contraintes-et-limitations)
7. [Configuration](#configuration)

---

## 🎓 Concept et Théorie

### Qu'est-ce que le Hashage de Mots de Passe?

Le **hashage de mots de passe** est une fonction cryptographique à sens unique qui transforme un mot de passe en texte en une chaîne de caractères fixe (hash). Contrairement au chiffrement, le hashage est **irréversible** - on ne peut pas retrouver le mot de passe original à partir du hash.

### Pourquoi Hasher les Mots de Passe?

**Sécurité:**
- Si la base de données est compromise, les mots de passe ne sont pas en clair
- Les attaquants ne peuvent pas lire directement les mots de passe
- Même avec le hash, retrouver le mot de passe original est très difficile

**Exemple:**
```
Mot de passe: "MyPassword123!"
Hash Argon2: "$argon2id$v=19$m=65536,t=2,p=1$rLdE9XwvOtOoaC4UjgwMxtTVFiSBX3lf5EUKrIr9LEY$yPp0yftYstFAaEBYlZTW8AeYOoVcO7o+DDrtp4kPh0fEr3SPoJvScKxn13zTIhRnyEjCm5funOGWBOzWzP6Vug"
```

### Qu'est-ce qu'Argon2?

**Argon2** est un algorithme de hashage de mots de passe qui a remporté le **Password Hashing Competition (PHC)** en 2015. Il est considéré comme l'algorithme le plus sécurisé actuellement disponible.

**Caractéristiques:**
- **Adaptatif:** Peut être configuré pour être plus lent (plus sécurisé)
- **Résistant aux GPU/ASIC:** Utilise la mémoire de manière intensive
- **Trois variantes:**
  - **Argon2d:** Résistant aux attaques par table arc-en-ciel
  - **Argon2i:** Résistant aux attaques par cache
  - **Argon2id:** Hybride (recommandé)

---

## 🤔 Pourquoi Argon2?

### Comparaison avec d'Autres Algorithmes

| Algorithme | Année | Résistance GPU | Résistance ASIC | Recommandation |
|------------|-------|---------------|-----------------|----------------|
| MD5 | 1992 | ❌ Faible | ❌ Faible | ❌ Déprécié |
| SHA-1 | 1995 | ❌ Faible | ❌ Faible | ❌ Déprécié |
| bcrypt | 1999 | ✅ Moyenne | ✅ Moyenne | ⚠️ Acceptable |
| scrypt | 2009 | ✅ Bonne | ✅ Bonne | ✅ Bon |
| **Argon2** | **2015** | ✅ **Excellente** | ✅ **Excellente** | ✅ **Recommandé** |

### Avantages d'Argon2

1. **Résistance aux Attaques par Force Brute**
   - Très lent à calculer (configurable)
   - Même avec GPU puissant, des millions d'années nécessaires

2. **Résistance aux Tables Arc-en-Ciel**
   - Chaque hash est unique grâce au salt
   - Impossible de pré-calculer des tables

3. **Résistance aux Attaques GPU/ASIC**
   - Utilise la mémoire de manière intensive
   - Les GPU/ASIC ne sont pas optimisés pour ce type d'opération

4. **Adaptatif**
   - Peut être rendu plus lent si nécessaire
   - S'adapte à l'évolution de la puissance de calcul

---

## 🏗️ Rôle dans l'Architecture

### Position dans l'Architecture

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│         (AuthController)                │
│  POST /auth/register                    │
│  POST /auth/login                       │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         BUSINESS LAYER                   │
│         (UserService)                    │
│  - register()                            │
│  - handleSuccessfulLogin()               │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│      SECURITY LAYER (Transversale)      │
│      (Argon2PasswordEncoder)            │
│  - encode() → Hash password             │
│  - matches() → Verify password           │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│         DATA LAYER                       │
│         (UserRepository)                 │
│  - save() → Store hashed password        │
└─────────────────────────────────────────┘
```

### Flux de Hashage

**Lors de l'Inscription:**
```
1. User → POST /auth/register { password: "MyPass123!" }
2. AuthController → UserService.register()
3. UserService → Argon2PasswordEncoder.encode("MyPass123!")
4. Argon2PasswordEncoder → Génère hash unique
5. UserService → Sauvegarde hash en base
```

**Lors de la Connexion:**
```
1. User → POST /auth/login { password: "MyPass123!" }
2. AuthController → Récupère user de la base
3. AuthController → Argon2PasswordEncoder.matches("MyPass123!", storedHash)
4. Argon2PasswordEncoder → Compare et retourne true/false
5. AuthController → Autorise ou refuse la connexion
```

---

## 💻 Implémentation Spring Boot

### Fichier: `Argon2PasswordEncoder.java`

**Localisation:** `com.example.auth_service.security.Argon2PasswordEncoder`

**Code Complet:**
```java
package com.example.auth_service.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Argon2PasswordEncoder implements PasswordEncoder {
    
    // Création de l'instance Argon2 avec configuration
    private final Argon2 argon2 = Argon2Factory.create(
        Argon2Factory.Argon2Types.ARGON2id,  // Type: Argon2id (hybride)
        16,  // Salt length: 16 bytes
        32   // Hash length: 32 bytes
    );
    
    /**
     * Encode (hash) un mot de passe en texte clair
     * 
     * @param rawPassword Le mot de passe en texte clair
     * @return Le hash Argon2 (format: $argon2id$v=19$m=...)
     */
    @Override
    public String encode(CharSequence rawPassword) {
        // Paramètres:
        // - iterations: 2 (nombre d'itérations)
        // - memory: 65536 KB = 64 MB (mémoire utilisée)
        // - parallelism: 1 (nombre de threads)
        return argon2.hash(
            2,      // Iterations
            65536,  // Memory (64 MB)
            1,      // Parallelism
            rawPassword.toString().toCharArray()
        );
    }
    
    /**
     * Vérifie si un mot de passe en texte clair correspond au hash stocké
     * 
     * @param rawPassword Le mot de passe en texte clair à vérifier
     * @param encodedPassword Le hash stocké en base de données
     * @return true si le mot de passe correspond, false sinon
     */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        try {
            // Argon2 extrait automatiquement les paramètres du hash
            // et vérifie si le mot de passe correspond
            return argon2.verify(encodedPassword, rawPassword.toString().toCharArray());
        } catch (Exception e) {
            // En cas d'erreur (hash invalide, etc.), retourne false
            return false;
        }
    }
}
```

### Intégration dans Spring Security

**Fichier:** `SecurityConfig.java`

```java
@Configuration
public class SecurityConfig {
    
    private final Argon2PasswordEncoder argon2PasswordEncoder;
    
    public SecurityConfig(Argon2PasswordEncoder argon2PasswordEncoder) {
        this.argon2PasswordEncoder = argon2PasswordEncoder;
    }
    
    /**
     * Bean Spring qui configure l'encodeur de mots de passe
     * Utilisé automatiquement par Spring Security
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return argon2PasswordEncoder; // ✅ Utilise Argon2 au lieu de bcrypt
    }
}
```

### Utilisation dans UserService

**Fichier:** `UserService.java`

```java
@Service
public class UserService {
    
    private final PasswordEncoder passwordEncoder; // Injecté automatiquement
    
    @Transactional
    public User register(String username, String rawPassword, String email, 
                         String fullName, Set<String> roleNames, String ipAddress) {
        
        // Hashage du mot de passe avec Argon2
        String hashedPassword = passwordEncoder.encode(rawPassword);
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPassword); // ✅ Hash stocké, jamais le mot de passe en clair
        user.setEmail(email);
        user.setFullName(fullName);
        
        // ... reste du code
        
        return userRepository.save(user);
    }
}
```

### Utilisation dans AuthController

**Fichier:** `AuthController.java`

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final PasswordEncoder passwordEncoder; // Injecté automatiquement
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, ...) {
        
        // Récupérer l'utilisateur de la base
        User user = userService.findByUsername(request.getUsername()).get();
        
        // Vérifier le mot de passe avec Argon2
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // ❌ Mot de passe incorrect
            userService.handleFailedLogin(...);
            return ResponseEntity.status(401).body(...);
        }
        
        // ✅ Mot de passe correct - continuer avec la connexion
        // ...
    }
}
```

---

## 🌐 Endpoints Utilisant Argon2

### 1. POST /auth/register

**Description:** Inscription d'un nouvel utilisateur (le mot de passe est hashé avec Argon2)

**Request:**
```http
POST http://localhost:8081/auth/register
Content-Type: application/json

{
  "username": "newuser",
  "password": "MySecurePassword123!",
  "email": "newuser@example.com",
  "fullName": "New User",
  "roles": ["PATIENT"]
}
```

**Traitement Interne:**
1. `AuthController.register()` reçoit la requête
2. `UserService.register()` est appelé
3. `passwordEncoder.encode("MySecurePassword123!")` est appelé
4. Argon2 génère un hash unique: `$argon2id$v=19$m=65536,t=2,p=1$...`
5. Le hash est stocké dans `users.password` (jamais le mot de passe en clair)

**Response (200 OK):**
```json
{
  "message": "User registered successfully",
  "username": "newuser"
}
```

**Ce qui est stocké en base:**
```sql
SELECT username, password FROM users WHERE username = 'newuser';
-- password: $argon2id$v=19$m=65536,t=2,p=1$rLdE9XwvOtOoaC4UjgwMxtTVFiSBX3lf5EUKrIr9LEY$...
-- ✅ Jamais "MySecurePassword123!" en clair
```

---

### 2. POST /auth/login

**Description:** Connexion (vérification du mot de passe avec Argon2)

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "newuser",
  "password": "MySecurePassword123!"
}
```

**Traitement Interne:**
1. `AuthController.login()` reçoit la requête
2. Récupère l'utilisateur de la base (avec le hash)
3. Appelle `passwordEncoder.matches("MySecurePassword123!", storedHash)`
4. Argon2 vérifie si le mot de passe correspond au hash
5. Si oui → Connexion réussie
6. Si non → Connexion échouée

**Response (200 OK) - Succès:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (401 Unauthorized) - Échec:**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4
}
```

**Processus de Vérification:**
```
1. User entre: "MySecurePassword123!"
2. Système récupère hash: "$argon2id$v=19$m=65536,t=2,p=1$..."
3. Argon2.hash("MySecurePassword123!", avec même salt) → nouveau hash
4. Comparaison: nouveau hash == hash stocké?
5. Si oui → ✅ Authentifié
6. Si non → ❌ Refusé
```

---

## ⚙️ Configuration

### Paramètres Argon2

**Fichier:** `Argon2PasswordEncoder.java`

```java
argon2.hash(
    2,      // Iterations (itérations)
    65536,  // Memory en KB (64 MB)
    1       // Parallelism (parallélisme)
);
```

**Explication des Paramètres:**

1. **Iterations (2):**
   - Nombre de fois que l'algorithme est exécuté
   - Plus élevé = plus lent = plus sécurisé
   - 2 est un bon équilibre performance/sécurité

2. **Memory (65536 KB = 64 MB):**
   - Quantité de mémoire utilisée
   - Plus élevé = plus résistant aux GPU/ASIC
   - 64 MB est optimal pour la plupart des cas

3. **Parallelism (1):**
   - Nombre de threads parallèles
   - 1 = séquentiel (plus sécurisé)
   - Peut être augmenté pour performance

### Format du Hash

**Structure:**
```
$argon2id$v=19$m=65536,t=2,p=1$salt$hash
│        │   │  │      │  │  │    │
│        │   │  │      │  │  │    └─ Hash final (32 bytes)
│        │   │  │      │  │  └────── Salt (16 bytes)
│        │   │  │      │  └───────── Parallelism
│        │   │  │      └──────────── Iterations
│        │   │  └─────────────────── Memory (KB)
│        │   └─────────────────────── Version
│        └─────────────────────────── Type (id = hybride)
└──────────────────────────────────── Préfixe
```

**Exemple Réel:**
```
$argon2id$v=19$m=65536,t=2,p=1$rLdE9XwvOtOoaC4UjgwMxtTVFiSBX3lf5EUKrIr9LEY$yPp0yftYstFAaEBYlZTW8AeYOoVcO7o+DDrtp4kPh0fEr3SPoJvScKxn13zTIhRnyEjCm5funOGWBOzWzP6Vug
```

---

## 🚫 Contraintes et Limitations

### Contraintes Techniques

1. **Performance:**
   - Le hashage prend ~100-200ms par mot de passe
   - Acceptable pour login/register
   - Peut être ralenti si nécessaire (augmenter iterations)

2. **Taille du Hash:**
   - Chaque hash fait ~100-150 caractères
   - Colonne `password` doit être `VARCHAR(255)` minimum

3. **Compatibilité:**
   - Nécessite la bibliothèque `argon2-jvm`
   - Compatible avec toutes les versions de Java 8+

### Contraintes de Sécurité

1. **Salt Unique:**
   - Chaque hash a un salt unique
   - Même mot de passe = hash différent
   - Empêche les attaques par table arc-en-ciel

2. **Irréversibilité:**
   - Impossible de retrouver le mot de passe original
   - Même avec le hash, pas de récupération possible
   - Reset de mot de passe nécessaire si oublié

3. **Pas de Migration Automatique:**
   - Si vous avez des mots de passe en bcrypt, ils doivent être migrés manuellement
   - L'utilisateur doit se reconnecter pour migrer

### Limitations

1. **Pas de Décryptage:**
   - Impossible de "décrypter" un hash
   - Seule la vérification est possible

2. **Pas de Récupération:**
   - Si le mot de passe est oublié, il faut le réinitialiser
   - Pas de "récupération" possible

3. **Performance:**
   - Plus lent que bcrypt (mais plus sécurisé)
   - Peut être un goulot d'étranglement si beaucoup de connexions simultanées

---

## 📊 Impact sur la Sécurité

### Avant Argon2 (Sans Hashage)

```
❌ Mot de passe stocké: "MyPassword123!"
❌ Si base compromise → Mots de passe lisibles
❌ Attaquant peut se connecter directement
```

### Avec Argon2

```
✅ Hash stocké: "$argon2id$v=19$m=65536,t=2,p=1$..."
✅ Si base compromise → Hash illisible
✅ Attaquant doit deviner le mot de passe (très difficile)
✅ Même avec GPU, millions d'années nécessaires
```

### Protection Contre les Attaques

| Type d'Attaque | Protection Argon2 |
|----------------|-------------------|
| Force brute | ✅ Très lent à calculer |
| Tables arc-en-ciel | ✅ Salt unique par hash |
| Attaques GPU | ✅ Utilise mémoire intensivement |
| Attaques ASIC | ✅ Non optimisé pour ASIC |
| Vol de base de données | ✅ Hash illisible |

---

## 🔍 Vérification dans la Base de Données

### Voir les Hashs Stockés

```sql
SELECT username, password FROM users;
```

**Résultat:**
```
username | password
---------|----------------------------------------------------------
admin    | $argon2id$v=19$m=65536,t=2,p=1$rLdE9XwvOtOoaC4UjgwMxtTVFiSBX3lf5EUKrIr9LEY$yPp0yftYstFAaEBYlZTW8AeYOoVcO7o+DDrtp4kPh0fEr3SPoJvScKxn13zTIhRnyEjCm5funOGWBOzWzP6Vug
admin2   | $argon2id$v=19$m=65536,t=2,p=1$XQ8jgLfnZXlrF7S3pFgkHeb2crLTFhnUG6kUzNJ0.JWh6K1xN5hRm$...
```

**✅ Important:** Vous ne verrez JAMAIS les mots de passe en clair!

---

## 🎯 Résumé

### Concept
Argon2 est un algorithme de hashage de mots de passe qui transforme de manière irréversible un mot de passe en texte en un hash sécurisé.

### Rôle dans l'Architecture
- **Couche transversale** (Security Layer)
- Utilisé par **UserService** (inscription)
- Utilisé par **AuthController** (connexion)
- **Protège** les mots de passe en base de données

### Implémentation
- **Classe:** `Argon2PasswordEncoder` implémente `PasswordEncoder`
- **Configuration:** 2 itérations, 64 MB mémoire, parallélisme 1
- **Intégration:** Bean Spring dans `SecurityConfig`

### Endpoints
- `POST /auth/register` - Hash le mot de passe lors de l'inscription
- `POST /auth/login` - Vérifie le mot de passe lors de la connexion

### Sécurité
- ✅ Résistant aux attaques par force brute
- ✅ Résistant aux tables arc-en-ciel
- ✅ Résistant aux GPU/ASIC
- ✅ Hash irréversible

---

**Documentation créée le:** 2026-01-14
**Version:** 1.0
