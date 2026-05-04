# 🔧 Quick Fix: Content-Type Error When Login Fails

## ❌ The Problem

You see this error in Spring Boot console:
```
Content-Type 'text/plain;charset=UTF-8' is not supported
```

**Result:** Nothing is saved to the database because Spring rejects the request before it reaches your controller!

## ✅ The Solution

Postman is sending `Content-Type: text/plain` instead of `Content-Type: application/json`.

### Step-by-Step Fix in Postman:

1. **Open your login request in Postman**

2. **Go to the "Headers" tab:**
   - Look for `Content-Type` header
   - If it says `text/plain`, DELETE it
   - Add a new header:
     - Key: `Content-Type`
     - Value: `application/json`

3. **Go to the "Body" tab:**
   - Select **"raw"** (not form-data, x-www-form-urlencoded, etc.)
   - **IMPORTANT:** In the dropdown next to "raw", select **"JSON"** (NOT "Text")
   - This automatically sets the correct Content-Type header

4. **Enter your JSON body:**
   ```json
   {
     "username": "testuser_argon2",
     "password": "wrongpassword"
   }
   ```

5. **Send the request again**

## ✅ Expected Result

After fixing the Content-Type:

**In Spring Boot Console, you should see:**
```
Hibernate: select u1_0.id,u1_0.account_locked... from users u1_0 where u1_0.username=?
Hibernate: update users set account_locked=?,failed_login_attempts=?... where id=?
Hibernate: insert into security_audit_logs (details,event_type,ip_address,success,timestamp,user_agent,username) values (?,?,?,?,?,?,?)
```

**In Postman Response:**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

**In Database:**
- `failed_login_attempts` increments to `1`
- New entry in `security_audit_logs` with `event_type = 'LOGIN_FAILED'`

## 🎯 Visual Guide

**Correct Postman Setup:**

```
┌─────────────────────────────────────┐
│ Headers Tab                        │
├─────────────────────────────────────┤
│ Content-Type: application/json  ✅  │
│ User-Agent: Mozilla/5.0...          │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Body Tab                            │
├─────────────────────────────────────┤
│ ○ none                              │
│ ○ form-data                         │
│ ○ x-www-form-urlencoded             │
│ ● raw  [JSON ▼]  ✅                  │
│ ○ binary                            │
│ ○ GraphQL                           │
│                                     │
│ {                                   │
│   "username": "testuser_argon2",    │
│   "password": "wrongpassword"       │
│ }                                   │
└─────────────────────────────────────┘
```

**Wrong Setup (causes error):**

```
┌─────────────────────────────────────┐
│ Body Tab                            │
├─────────────────────────────────────┤
│ ● raw  [Text ▼]  ❌                 │
│                                     │
│ This sends Content-Type: text/plain │
└─────────────────────────────────────┘
```

## 🔍 How to Verify It's Fixed

1. **Check the Headers tab** - Should show `Content-Type: application/json`
2. **Check the Body tab** - Dropdown should say "JSON" not "Text"
3. **Send request** - Should see SQL queries in console
4. **Check database** - `failed_login_attempts` should increment

---

**Once you fix the Content-Type, everything will work!** 🎉
