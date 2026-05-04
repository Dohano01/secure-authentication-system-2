# 🔄 Step 3: Refresh Token Implementation - Complete Guide

## ✅ What Was Implemented

### 📦 New Components Created

1. **RefreshToken Entity** (`model/RefreshToken.java`)
   - Stores refresh tokens in database
   - Tracks IP address, user agent, device type
   - Includes token version for invalidation
   - Automatic expiry date management

2. **RefreshTokenRepository** (`repository/RefreshTokenRepository.java`)
   - Database operations for refresh tokens
   - Methods to find, revoke, and clean up tokens

3. **RefreshTokenService** (`service/RefreshTokenService.java`)
   - Business logic for token management
   - Token generation, validation, and revocation
   - Session management

4. **Updated Components:**
   - `AuthResponse` - Now includes `refreshToken` field
   - `JwtUtil` - Added token version support
   - `AuthController` - Added 4 new endpoints
   - `UserService` - Added save method

### 🔄 New Endpoints

#### 1. `POST /auth/refresh` - Refresh Access Token
**Purpose:** Get a new access token using a valid refresh token

**Request:**
```json
{
  "refreshToken": "uuid-refresh-token-here"
}
```

**Response (200 OK):**
```json
{
  "token": "new-access-token-jwt",
  "refreshToken": "same-refresh-token-uuid"
}
```

**Error Responses:**
- `400` - Missing refresh token
- `401` - Invalid, expired, or revoked refresh token
- `423` - Account is locked

---

#### 2. `POST /auth/logout` - Logout Current Session
**Purpose:** Revoke the current refresh token (logout from one device)

**Request:**
```json
{
  "refreshToken": "uuid-refresh-token-here"
}
```

**Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

---

#### 3. `POST /auth/logout-all` - Logout All Devices
**Purpose:** Revoke all refresh tokens for the user (logout from all devices)

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "message": "Logged out from all devices successfully",
  "username": "admin"
}
```

**What it does:**
- Increments user's `token_version`
- Revokes all refresh tokens
- Invalidates all existing access tokens (they won't pass version check)

---

#### 4. `GET /auth/sessions` - View Active Sessions
**Purpose:** Get all active sessions (devices) for the current user

**Headers:**
```
Authorization: Bearer <access-token>
```

**Response (200 OK):**
```json
{
  "username": "admin",
  "totalSessions": 2,
  "sessions": [
    {
      "id": 1,
      "deviceType": "Desktop",
      "ipAddress": "203.0.113.45",
      "userAgent": "PostmanRuntime/7.51.0",
      "createdAt": "2026-01-13T23:44:01",
      "expiryDate": "2026-02-12T23:44:01"
    },
    {
      "id": 2,
      "deviceType": "Mobile",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0 (iPhone...)",
      "createdAt": "2026-01-13T20:00:00",
      "expiryDate": "2026-02-12T20:00:00"
    }
  ]
}
```

---

### 🔐 Security Features Implemented

✅ **Access tokens with token version**
- Each access token includes `tokenVersion` claim
- Tokens are invalidated when version changes (logout-all)

✅ **Refresh tokens stored in database**
- All refresh tokens persisted
- Can track and revoke individual tokens

✅ **Automatic token rotation** (ready for implementation)
- Framework in place for refresh token rotation
- Currently returns same token, can be enhanced

✅ **Token revocation**
- Individual token revocation (logout)
- Bulk revocation (logout-all)

✅ **Global invalidation**
- `logout-all` increments token version
- All existing tokens become invalid

✅ **Session tracking by device**
- Each refresh token tracks device info
- View all active sessions

---

## 📋 Updated Login Response

The `/auth/login` endpoint now returns both tokens:

**Before:**
```json
{
  "token": "access-token-jwt"
}
```

**After:**
```json
{
  "token": "access-token-jwt",
  "refreshToken": "uuid-refresh-token"
}
```

---

## 🧪 Testing Guide

### Test 1: Login and Get Refresh Token

**Request:**
```http
POST http://localhost:8081/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Save both tokens for next tests!**

---

### Test 2: Refresh Access Token

**⚠️ IMPORTANT:** Use the **actual refresh token** you received from the login response, NOT the example token!

**Request:**
```http
POST http://localhost:8081/auth/refresh
Content-Type: application/json

{
  "refreshToken": "551761c7-9d66-41d1-b4ad-9857f0b778a9"
}
```
**Note:** Replace `551761c7-9d66-41d1-b4ad-9857f0b778a9` with the refresh token you got from Test 1!

**Response (200 OK):**
```json
{
  "token": "new-access-token-jwt",
  "refreshToken": "551761c7-9d66-41d1-b4ad-9857f0b778a9"
}
```

**Error (401 Unauthorized) - If token doesn't exist:**
```json
{
  "error": "Invalid refresh token",
  "message": "Refresh token is invalid, expired, or revoked"
}
```

**Common Issues:**
- ❌ Using example token instead of real token from login
- ❌ Token expired (default: 30 days)
- ❌ Token revoked (after logout)
- ❌ Token version mismatch (after logout-all)

---

### Test 3: View Active Sessions

**⚠️ IMPORTANT:** Use the **access token** (JWT), NOT the refresh token!

**Request:**
```http
GET http://localhost:8081/auth/sessions
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJ0b2tlblZlcnNpb24iOjAsInJvbGVzIjpbIkFETU1OIl0sInN1YiI6IkFETUlOIiwiaWF0IjoxNzY4MzQ2NzY5LCJleHAiOjE3NjgzNTAzNjF9.ESf3ysNwmJgSbem3oC7Qy...
```

**Where to get the access token:**
1. From login response: `"token": "eyJhbGci..."` ← Use THIS
2. From refresh response: `"token": "eyJhbGci..."` ← Or THIS

**Format:**
- Header name: `Authorization`
- Header value: `Bearer <your-access-token-here>`
- ❌ Don't use: `Bearer <refresh-token>` (refresh tokens are UUIDs, not JWTs)

**Example in Postman:**
- Go to **Headers** tab
- Add header:
  - Key: `Authorization`
  - Value: `Bearer eyJhbGciOiJIUzUxMiJ9.eyJ0b2tlblZlcnNpb24iOjAsInJvbGVzIjpbIkFETU1OIl0sInN1YiI6IkFETUlOIiwiaWF0IjoxNzY4MzQ2NzY5LCJleHAiOjE3NjgzNTAzNjF9.ESf3ysNwmJgSbem3oC7Qy...`

**Response (200 OK):** See example above

---

### Test 4: Logout Current Session

**Request:**
```http
POST http://localhost:8081/auth/logout
Content-Type: application/json

{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```json
{
  "message": "Logged out successfully"
}
```

**Verify:** Try to refresh with the same token - should get 401 error

---

### Test 5: Logout All Devices

**Request:**
```http
POST http://localhost:8081/auth/logout-all
Authorization: Bearer <access-token>
```

**Response:**
```json
{
  "message": "Logged out from all devices successfully",
  "username": "admin"
}
```

**Verify:**
1. All refresh tokens are revoked
2. Token version incremented
3. Old access tokens no longer work

---

## 📊 Database Verification

### Check Refresh Tokens Table
```sql
SELECT 
    id,
    token,
    user_id,
    expiry_date,
    created_at,
    ip_address,
    device_type,
    token_version,
    revoked
FROM refresh_tokens
WHERE user_id = (SELECT id FROM users WHERE username = 'admin');
```

### Check Token Version
```sql
SELECT username, token_version 
FROM users 
WHERE username = 'admin';
```

---

## ⚙️ Configuration

Added to `application.properties`:
```properties
jwt.refresh-expiration-days=30
```

Default: Refresh tokens expire after 30 days (configurable)

---

## 🔒 Security Notes

1. **Token Version:** When `logout-all` is called, the user's `token_version` is incremented. All existing access tokens (even if not expired) become invalid because they have the old version.

2. **Refresh Token Validation:** Refresh tokens are validated for:
   - Existence in database
   - Not expired
   - Not revoked
   - Token version matches user's current version

3. **Automatic Cleanup:** The `deleteExpiredTokens()` method can be called by a scheduled task to clean up expired tokens.

---

## ✅ Summary

**Step 3 is now complete!** All refresh token functionality has been implemented:

- ✅ Refresh token generation on login
- ✅ Token refresh endpoint
- ✅ Logout (single session)
- ✅ Logout all (all sessions)
- ✅ View active sessions
- ✅ Token version tracking
- ✅ Automatic invalidation

**Your authentication service now has full refresh token support!** 🎉
