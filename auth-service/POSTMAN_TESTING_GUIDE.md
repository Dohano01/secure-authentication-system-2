# Postman Testing Guide - Security Features

## 🚀 Setup Instructions

### 1. Base URL Configuration
- **Base URL:** `http://localhost:8081`
- **Service Port:** `8081` (as per `application.properties`)

### 2. Postman Environment Variables (Optional but Recommended)
Create a Postman environment with:
- `base_url`: `http://localhost:8081`
- `token`: (will be set after login)

---

## 📋 Test Scenarios

## Test 1: ✅ Successful Login with Device Detection

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/login`
- **Headers:**
  ```
  Content-Type: application/json
  User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
  ```
- **Body (raw JSON):**
  ```json
  {
    "username": "admin",
    "password": "admin123"
  }
  ```
  **Note:** Make sure `"admin"` exists in your database's `username` column. If not, use an existing username from your database.

### Expected Response (200 OK)
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### What to Verify:
1. ✅ Status code: `200 OK`
2. ✅ Token is returned
3. ✅ Check database: `last_login_ip` and `last_login_device` should be updated
4. ✅ Check `security_audit_logs` table: Should have `LOGIN_SUCCESS` entry with IP and device

---

## Test 2: 📱 Test Mobile Device Detection

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/login`
- **Headers:**
  ```
  Content-Type: application/json
  User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15
  ```
- **Body (raw JSON):**
  ```json
  {
    "username": "admin2",
    "password": "admin123"
  }
  ```

### Expected Response (200 OK)
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### What to Verify:
1. ✅ Status code: `200 OK`
2. ✅ Check database: `last_login_device` should be `"Mobile"`

---

## Test 3: 📱 Test Tablet Device Detection

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/login`
- **Headers:**
  ```
  Content-Type: application/json
  User-Agent: Mozilla/5.0 (iPad; CPU OS 14_0 like Mac OS X) AppleWebKit/605.1.15
  ```
- **Body (raw JSON):**
  ```json
  {
    "username": "admin3",
    "password": "admin123"
  }
  ```

### Expected Response (200 OK)
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### What to Verify:
1. ✅ Status code: `200 OK`
2. ✅ Check database: `last_login_device` should be `"Tablet"`

---

## Test 4: ❌ Failed Login Attempt (1st attempt)

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/login`
- **Headers:**
  ```
  Content-Type: application/json
  User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
  X-Forwarded-For: 192.168.1.100
  ```
  **⚠️ CRITICAL:** Make sure `Content-Type: application/json` is set in the Headers tab! If you see error `Content-Type 'text/plain' is not supported`, Postman is sending the wrong content type.

- **Body Tab Settings:**
  - Select **"raw"** (not form-data, x-www-form-urlencoded, etc.)
  - In the dropdown next to "raw", select **"JSON"** (NOT "Text")
  - This ensures Postman sends `Content-Type: application/json`

- **Body (raw JSON):**
  ```json
  {
    "username": "testuser_argon2",
    "password": "wrongpassword"
  }
  ```
  **⚠️ IMPORTANT:** Use the actual `username` from your database, NOT the email. Based on your database, use `"testuser_argon2"` (username column), not `"test@example.com"` (email column).

### Expected Response (401 Unauthorized)

**If user EXISTS but password is wrong:**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect",
  "remainingAttempts": 4,
  "warning": ""
}
```

**If user DOES NOT EXIST:**
```json
{
  "error": "Invalid credentials",
  "message": "Username or password is incorrect"
}
```
**Note:** When user doesn't exist, `remainingAttempts` and `warning` fields are NOT included in the response.

### What to Verify:
1. ✅ Status code: `401 Unauthorized`
2. ✅ **If user exists:** `remainingAttempts: 4` (5 - 1 = 4)
3. ✅ **If user doesn't exist:** Response will NOT have `remainingAttempts` field
4. ✅ Check database: `failed_login_attempts` should be `1` (only if user exists)
5. ✅ Check `security_audit_logs`: Should have `LOGIN_FAILED` entry with IP `192.168.1.100`

---

## Test 5: ❌ Multiple Failed Attempts (2nd-4th attempts)

### Request
**Repeat Test 4 three more times** (same wrong password)

### Expected Responses:
- **2nd attempt:** `remainingAttempts: 3`
- **3rd attempt:** `remainingAttempts: 2`, `warning: "Account will be locked after 2 more failed attempts"`
- **4th attempt:** `remainingAttempts: 1`, `warning: "Account will be locked after 1 more failed attempts"`

### What to Verify:
1. ✅ Each attempt increments `failed_login_attempts` in database
2. ✅ Warning appears when `remainingAttempts <= 2`
3. ✅ Each failed attempt is logged in `security_audit_logs`

---

## Test 6: 🔒 Account Lock After 5 Failed Attempts

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/login`
- **Headers:**
  ```
  Content-Type: application/json
  User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
  ```
- **Body (raw JSON):**
  ```json
  {
    "username": "testuser_argon2",
    "password": "wrongpassword"
  }
  ```
  **⚠️ IMPORTANT:** Use `"testuser_argon2"` (the username from your database), NOT `"test@example.com"` (the email).
  
  **This should be the 5th failed attempt**

### Expected Response (423 Locked)
```json
{
  "error": "Account locked",
  "message": "Too many failed login attempts. Account locked for 15 minutes.",
  "failedAttempts": 5
}
```

### What to Verify:
1. ✅ Status code: `423 Locked`
2. ✅ Check database:
   - `account_locked` = `true`
   - `failed_login_attempts` = `5`
   - `lock_time` = current timestamp
3. ✅ Check `security_audit_logs`: Should have `ACCOUNT_LOCKED` entry

---

## Test 7: 🔒 Attempt Login While Account is Locked

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/login`
- **Headers:**
  ```
  Content-Type: application/json
  ```
- **Body (raw JSON):**
  ```json
  {
    "username": "testuser_argon2",
    "password": "correctpassword"
  }
  ```
  **⚠️ IMPORTANT:** Use `"testuser_argon2"` (the username from your database).
  
  **Even with correct password, account should be locked**

### Expected Response (423 Locked)
```json
{
  "error": "Account locked",
  "message": "Too many failed login attempts. Account is locked.",
  "remainingLockTimeMinutes": 15,
  "tryAgainIn": "15 minutes"
}
```

### What to Verify:
1. ✅ Status code: `423 Locked`
2. ✅ `remainingLockTimeMinutes` shows time remaining
3. ✅ Even correct password is rejected

---

## Test 8: ⏱️ Test Auto-Unlock (After 15 Minutes)

### Option A: Wait 15 Minutes
1. Lock an account (Test 6)
2. Wait 15 minutes
3. Try to login with correct password
4. Should succeed (account auto-unlocked)

### Option B: Manual Database Update (For Testing)
**If you want to test immediately without waiting:**

1. Lock account (Test 6)
2. Update database directly:
   ```sql
   UPDATE users 
   SET lock_time = lock_time - INTERVAL '15 minutes'
   WHERE username = 'test@example.com';
   ```
3. Try login again - should succeed

### Expected Response (200 OK)
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### What to Verify:
1. ✅ Status code: `200 OK`
2. ✅ Check database:
   - `account_locked` = `false`
   - `failed_login_attempts` = `0`
   - `lock_time` = `NULL`

---

## Test 9: 🛡️ View Login History

### Request
- **Method:** `GET`
- **URL:** `http://localhost:8081/auth/login-history/testuser_argon2`
  
  **⚠️ IMPORTANT:** Use the actual `username` from your database, NOT the email.
- **Headers:**
  ```
  Content-Type: application/json
  ```

### Expected Response (200 OK)
```json
[
  {
    "id": 1,
    "username": "test@example.com",
    "eventType": "LOGIN_SUCCESS",
    "ipAddress": "0:0:0:0:0:0:0:1",
    "userAgent": "Mozilla/5.0...",
    "details": "User logged in successfully",
    "timestamp": "2026-01-13T16:41:10.389628",
    "success": true
  },
  {
    "id": 2,
    "username": "test@example.com",
    "eventType": "LOGIN_FAILED",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "details": "Login failed: Invalid password",
    "timestamp": "2026-01-13T16:40:00.000000",
    "success": false
  },
  {
    "id": 3,
    "username": "test@example.com",
    "eventType": "ACCOUNT_LOCKED",
    "ipAddress": "192.168.1.100",
    "userAgent": null,
    "details": "Account locked: Too many failed login attempts (5)",
    "timestamp": "2026-01-13T16:39:00.000000",
    "success": false
  }
]
```

### What to Verify:
1. ✅ Status code: `200 OK`
2. ✅ Returns all security events for the user
3. ✅ Ordered by timestamp (most recent first)
4. ✅ Includes IP addresses, user agents, event types

---

## Test 10: 🔓 Manual Account Unlock (Admin Feature)

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/unlock/testuser_argon2`
  
  **⚠️ IMPORTANT:** Use the actual `username` from your database, NOT the email.
- **Headers:**
  ```
  Content-Type: application/json
  ```

### Expected Response (200 OK)
```json
{
  "message": "Account unlocked successfully",
  "username": "test@example.com"
}
```

### What to Verify:
1. ✅ Status code: `200 OK`
2. ✅ Check database:
   - `account_locked` = `false`
   - `failed_login_attempts` = `0`
   - `lock_time` = `NULL`
3. ✅ Check `security_audit_logs`: Should have `ACCOUNT_UNLOCKED` entry

---

## Test 11: 🌐 IP Tracking Test

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8081/auth/login`
- **Headers:**
  ```
  Content-Type: application/json
  X-Forwarded-For: 203.0.113.45
  X-Real-IP: 198.51.100.10
  ```
  **⚠️ IMPORTANT:** 
  - Make sure the header name is exactly `X-Forwarded-For` (case-sensitive in Postman)
  - Check the Headers tab to verify the header is actually added (not just typed)
  - If you see `0:0:0:0:0:0:0:1` in database, the header wasn't received (see troubleshooting below)

- **Body (raw JSON):**
  ```json
  {
    "username": "admin",
    "password": "admin123"
  }
  ```

### What to Verify:
1. ✅ Check database: `last_login_ip` should be `203.0.113.45` (X-Forwarded-For takes priority)
2. ✅ Check `security_audit_logs`: IP address should be `203.0.113.45`

### Troubleshooting: Getting `0:0:0:0:0:0:0:1` Instead of Custom IP

**If you see `0:0:0:0:0:0:0:1` (IPv6 localhost) in the database:**

1. **Verify Header is Actually Sent:**
   - In Postman, after sending the request, go to the **"Console"** tab (bottom of Postman)
   - Look at the request details - verify `X-Forwarded-For` header is listed
   - Or use Postman's "Code" button to see the generated curl command

2. **Check Header Name:**
   - Header must be exactly: `X-Forwarded-For` (capital X, capital F, etc.)
   - Postman is case-sensitive for header names
   - Make sure there are no extra spaces

3. **Verify in Spring Boot Console:**
   - The code has been updated to handle case-insensitive headers
   - Restart your application after the code change
   - Check if the header is being received

4. **Alternative: Test with Different IP Format:**
   - Try IPv4 format: `X-Forwarded-For: 192.168.1.100`
   - Or use: `X-Real-IP: 198.51.100.10`

**Note:** `0:0:0:0:0:0:0:1` is the IPv6 loopback address (equivalent to `127.0.0.1`). This is normal when:
- Testing locally without a proxy
- The custom header isn't being sent/received
- The request is coming directly from localhost

**In production with a real proxy/load balancer, the `X-Forwarded-For` header will be automatically added and will work correctly.**

---

## Test 12: 📝 Verify Audit Logging for All Events

### Steps:
1. Perform a successful login
2. Perform a failed login
3. Lock an account (5 failed attempts)
4. Unlock an account manually
5. Check `security_audit_logs` table

### Expected Logs:
- ✅ `LOGIN_SUCCESS` entries
- ✅ `LOGIN_FAILED` entries
- ✅ `ACCOUNT_LOCKED` entry
- ✅ `ACCOUNT_UNLOCKED` entry (if manually unlocked)

### What to Verify:
1. ✅ All events have timestamps
2. ✅ All events have IP addresses (except some unlock events)
3. ✅ Success/failure status is correct
4. ✅ Details field contains descriptive information

---

## 🎯 Complete Test Flow (Recommended Order)

1. **Test 1:** Successful login (Desktop)
2. **Test 2:** Mobile device detection
3. **Test 3:** Tablet device detection
4. **Test 4-5:** Failed login attempts (1-4)
5. **Test 6:** Account lock (5th failed attempt)
6. **Test 7:** Attempt login while locked
7. **Test 9:** View login history (verify all events logged)
8. **Test 10:** Manual unlock
9. **Test 1:** Successful login after unlock
10. **Test 8:** Test auto-unlock (optional, requires waiting or DB manipulation)
//////////////////////////////////
All security features are working:
Brute-force protection
Auto-unlock
Audit logging
IP tracking — working (confirmed)
Device detection — working (confirmed)
Security monitoring
///////////////////////////////
---

## 📊 Database Verification Queries

### Check User Security Status
```sql
SELECT 
    username,
    failed_login_attempts,
    account_locked,
    lock_time,
    last_login,
    last_failed_login,
    last_login_ip,
    last_login_device
FROM users
WHERE username = 'testuser_argon2';
```

### Check Audit Logs
```sql
SELECT 
    id,
    username,
    event_type,
    ip_address,
    user_agent,
    details,
    timestamp,
    success
FROM security_audit_logs
WHERE username = 'testuser_argon2'
ORDER BY timestamp DESC;
```

### Check Failed Attempts Count
```sql
SELECT 
    username,
    COUNT(*) as failed_attempts
FROM security_audit_logs
WHERE username = 'testuser_argon2'
  AND event_type = 'LOGIN_FAILED'
  AND timestamp > NOW() - INTERVAL '1 hour'
GROUP BY username;
```

### List All Usernames (to find correct username to use)
```sql
SELECT id, username, email FROM users;
```

---

## 🐛 Troubleshooting

### Issue: Not seeing `remainingAttempts` in response
**Problem:** Response only shows `error` and `message`, but no `remainingAttempts` or `warning` fields.

**Cause:** The username you're using doesn't exist in the database.

**Solution:**
1. **Check your database** - Verify the username exists:
   ```sql
   SELECT username, email FROM users;
   ```
2. **Use the `username` column, NOT the `email` column** in your login request
   - ✅ Correct: `"username": "testuser_argon2"` (from username column)
   - ❌ Wrong: `"username": "test@example.com"` (this is the email column)
3. **The API searches by `username` field, not email**
4. **If user doesn't exist:** The response won't include `remainingAttempts` because there's no user record to track attempts for

**Expected Behavior:**
- **User exists + wrong password:** Response includes `remainingAttempts` and `warning`
- **User doesn't exist:** Response only includes `error` and `message` (no `remainingAttempts`)

### Issue: Account not locking after 5 attempts
**Solution:** 
- Verify `failed_login_attempts` is incrementing in database
- Check that you're using the same username for all attempts
- Ensure database updates are being committed
- **Make sure you're using an existing username** (see issue above)

### Issue: Auto-unlock not working
**Solution:**
- Verify `lock_time` is set correctly in database
- Check system time is correct
- The unlock happens during login check, so try logging in again

### Issue: IP address showing as `0:0:0:0:0:0:0:1` instead of custom IP
**Problem:** You set `X-Forwarded-For` header but still see `0:0:0:0:0:0:0:1` in database.

**Solutions:**
1. **Verify header is actually sent:**
   - In Postman, check the "Console" tab after sending request
   - Verify `X-Forwarded-For` appears in the request headers
   - Make sure header name is exactly `X-Forwarded-For` (case-sensitive)

2. **Check Postman Headers tab:**
   - Header must be in the "Headers" tab, not just typed somewhere
   - Make sure it's enabled (checkbox checked)
   - No extra spaces in header name or value

3. **Restart application:**
   - The code has been updated to handle case-insensitive headers
   - Restart Spring Boot after code changes

4. **Test with curl to verify:**
   ```bash
   curl -X POST http://localhost:8081/auth/login \
     -H "Content-Type: application/json" \
     -H "X-Forwarded-For: 203.0.113.45" \
     -d '{"username":"admin","password":"admin123"}'
   ```

5. **Note:** `0:0:0:0:0:0:0:1` is IPv6 localhost - this is normal when:
   - Testing locally without proxy
   - Header isn't being received
   - In production with real proxy, `X-Forwarded-For` will work automatically

### Issue: Device detection not working
**Solution:**
- Ensure `User-Agent` header is being sent
- Check header value matches expected patterns (mobile, tablet, etc.)
- Default is "Desktop" if no match found

### Issue: Response body is empty or shows "Pass the correct auth credentials"
**Solution:**
- This might be Spring Security's default error message
- Check that your request has `Content-Type: application/json` header
- Verify the request body is valid JSON
- Check server logs for actual error messages

---

## ✅ Success Criteria Checklist

After running all tests, verify:

- [ ] ✅ Successful logins work and generate tokens
- [ ] ✅ Failed logins increment attempt counter
- [ ] ✅ Account locks after exactly 5 failed attempts
- [ ] ✅ Locked accounts reject login attempts
- [ ] ✅ Account auto-unlocks after 15 minutes
- [ ] ✅ IP addresses are tracked and stored
- [ ] ✅ Device types are detected (Desktop/Mobile/Tablet)
- [ ] ✅ All security events are logged in audit table
- [ ] ✅ Login history endpoint returns all events
- [ ] ✅ Manual unlock works correctly


Security features status
All 6 security features are working:
Brute-force protection — Account locks after 5 failed attempts
Auto-unlock — Automatically unlocks after 15 minutes
Audit logging — Every security event is logged (confirmed)
IP tracking — Tracks login attempts by IP address (confirmed — 203.0.113.45 stored)
Device detection — Identifies Desktop/Mobile/Tablet (confirmed — Desktop detected)
Security monitoring — Complete login history viewable
---

**Happy Testing! 🚀**
