# Troubleshooting: Failed Login Attempts Not Saving to Database

## 🔍 Problem
When you try to login with wrong password using `testuser_argon2`, the failed attempt is not being saved to the database (no update to `failed_login_attempts` and no entry in `security_audit_logs`).

## ✅ Solution Steps

### Step 1: Restart Your Application
After the security configuration change, **restart your Spring Boot application**:
1. Stop the application (Ctrl+C in terminal)
2. Start it again
3. Wait for it to fully start

### Step 2: Verify the Security Configuration Change
The security config has been updated to disable HTTP Basic Auth. Make sure the change is applied:
- File: `SecurityConfig.java`
- Line 32: Should show `.httpBasic().disable();`

### Step 3: Test Again in Postman

**Request:**
- Method: `POST`
- URL: `http://localhost:8081/auth/login`
- Headers:
  ```
  Content-Type: application/json
  ```
- Body (raw JSON):
  ```json
  {
    "username": "testuser_argon2",
    "password": "wrongpassword"
  }
  ```

**Expected Response:**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

### Step 4: Check Database After Request

**Check `users` table:**
```sql
SELECT 
    username,
    failed_login_attempts,
    account_locked,
    last_failed_login
FROM users
WHERE username = 'testuser_argon2';
```

**Expected:** `failed_login_attempts` should be `1` (or incrementing if you try multiple times)

**Check `security_audit_logs` table:**
```sql
SELECT 
    id,
    username,
    event_type,
    ip_address,
    details,
    timestamp
FROM security_audit_logs
WHERE username = 'testuser_argon2'
ORDER BY timestamp DESC
LIMIT 5;
```

**Expected:** Should see a new `LOGIN_FAILED` entry with:
- `event_type`: `LOGIN_FAILED`
- `details`: `Login failed: Invalid password`
- `success`: `false`

---

## 🐛 If Still Not Working

### ⚠️ COMMON ERROR: Content-Type Issue

**If you see this error in console:**
```
Content-Type 'text/plain;charset=UTF-8' is not supported
```

**This means Postman is sending the wrong Content-Type header!**

**Fix:**
1. In Postman, go to the **Headers** tab
2. Make sure you have: `Content-Type: application/json`
3. In the **Body** tab:
   - Select **"raw"**
   - In the dropdown next to "raw", select **"JSON"** (NOT "Text")
4. This ensures Postman sends `Content-Type: application/json` automatically

**Without the correct Content-Type, Spring rejects the request BEFORE it reaches your controller, so nothing is saved to the database!**

### Check 1: Verify Application Logs
Look at your Spring Boot console output. You should see:
- SQL queries being executed (if `spring.jpa.show-sql=true`)
- No exceptions or errors
- **If you see `HttpMediaTypeNotSupportedException`, fix the Content-Type header (see above)**

### Check 2: Verify Transaction is Committing
The `handleFailedLogin` method is `@Transactional`, so it should auto-commit. If you see SQL in logs but no database changes:
- Check database connection
- Verify you're looking at the correct database
- Check if there are multiple database instances

### Check 3: Test with Correct Password First
Try logging in with the **correct password** to verify the endpoint works:
```json
{
  "username": "testuser_argon2",
  "password": "test123"  // Use the actual password for this user
}
```

If this works, then try wrong password again.

### Check 4: Verify User Exists
```sql
SELECT id, username, email FROM users WHERE username = 'testuser_argon2';
```

Should return exactly 1 row.

### Check 5: Check for Exceptions
Look for any exceptions in the application logs. Common issues:
- Database connection errors
- Transaction rollback errors
- Constraint violations

---

## 🔧 Alternative: Manual Database Test

If the application still doesn't update the database, you can manually test the database connection:

```sql
-- Manually increment failed attempts (for testing only)
UPDATE users 
SET failed_login_attempts = failed_login_attempts + 1,
    last_failed_login = NOW()
WHERE username = 'testuser_argon2';

-- Check if it updated
SELECT failed_login_attempts FROM users WHERE username = 'testuser_argon2';
```

If this works, the database is fine and the issue is in the application code.

---

## 📝 Expected Behavior After Fix

1. **First failed attempt:**
   - `failed_login_attempts` = `1`
   - New `LOGIN_FAILED` entry in audit logs
   - Response includes `remainingAttempts: 4`

2. **Second failed attempt:**
   - `failed_login_attempts` = `2`
   - Another `LOGIN_FAILED` entry
   - Response includes `remainingAttempts: 3`

3. **Fifth failed attempt:**
   - `failed_login_attempts` = `5`
   - `account_locked` = `true`
   - `lock_time` = current timestamp
   - `LOGIN_FAILED` + `ACCOUNT_LOCKED` entries
   - Response: `423 Locked`

---

## ✅ Success Indicators

After restarting and testing, you should see:
- ✅ Response includes `remainingAttempts` field
- ✅ `failed_login_attempts` increments in database
- ✅ New entries appear in `security_audit_logs` table
- ✅ No Spring Security error messages

**If all these work, the issue is resolved!** 🎉
