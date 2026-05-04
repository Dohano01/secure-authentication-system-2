# Why You're Not Seeing `remainingAttempts` in the Response

## 🔍 The Issue

You're getting a `401 Unauthorized` response, but it only shows:
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect"
}
```

Instead of the expected:
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

## ✅ The Solution

**The username you're using doesn't exist in the database!**

### How the Code Works

Looking at `AuthController.java`:

1. **If user doesn't exist** (line 61-66):
   ```java
   if (userOpt.isEmpty()) {
       return ResponseEntity.status(401).body(Map.of(
           "error", "Invalid credentials",
           "message", "Username or password is incorrect"
       ));
   }
   ```
   ❌ **No `remainingAttempts` field** - because there's no user record to track!

2. **If user exists but password is wrong** (line 98-103):
   ```java
   int remainingAttempts = 5 - user.getFailedLoginAttempts();
   return ResponseEntity.status(401).body(Map.of(
       "error", "Invalid credentials",
       "message": "Username or password is incorrect",
       "remainingAttempts", remainingAttempts,
       "warning", remainingAttempts <= 2 ? "Account will be locked..." : ""
   ));
   ```
   ✅ **Includes `remainingAttempts` field** - because user exists and we can track attempts!

### What You Need to Do

1. **Check your database** to see what usernames exist:
   ```sql
   SELECT id, username, email FROM users;
   ```

2. **Use the `username` column, NOT the `email` column** in your Postman request:
   - ✅ **Correct:** `"username": "testuser_argon2"` (from username column)
   - ❌ **Wrong:** `"username": "test@example.com"` (this is the email column)

3. **Based on your database data:**
   - Username: `"testuser_argon2"` ✅
   - Email: `"test@example.com"` ❌ (don't use this in the username field)

### Example Correct Request

```json
{
  "username": "testuser_argon2",
  "password": "wrongpassword"
}
```

### Expected Response (with existing user)

```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

---

## 📊 Quick Reference

| Scenario | Response Includes `remainingAttempts`? |
|----------|----------------------------------------|
| User doesn't exist | ❌ NO |
| User exists + wrong password | ✅ YES |
| User exists + correct password | ✅ N/A (returns token instead) |

---

**Once you use an existing username, you'll see the `remainingAttempts` field!** 🎯
