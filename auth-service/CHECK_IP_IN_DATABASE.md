# Check IP Address in Database

## 🔍 SQL Queries to Check IP Address

### Check Last Login IP for User
```sql
SELECT 
    username,
    last_login_ip,
    last_login,
    last_login_device
FROM users
WHERE username = 'admin';
```

### Check IP in Audit Logs (Most Recent)
```sql
SELECT 
    id,
    username,
    event_type,
    ip_address,
    timestamp,
    details
FROM security_audit_logs
WHERE username = 'admin'
ORDER BY timestamp DESC
LIMIT 5;
```

### Check All Recent Login Events with IPs
```sql
SELECT 
    username,
    event_type,
    ip_address,
    timestamp
FROM security_audit_logs
WHERE event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILED')
ORDER BY timestamp DESC
LIMIT 10;
```

## ✅ Expected Results

**If IP header is working correctly:**
- `last_login_ip` should be: `203.0.113.45`
- `security_audit_logs.ip_address` should be: `203.0.113.45`

**If you still see `0:0:0:0:0:0:0:1`:**
- The header might not be reaching the controller
- Check if you restarted the application after code changes
- Verify the header is actually being sent (see troubleshooting below)

## 🐛 Troubleshooting

### If Database Still Shows `0:0:0:0:0:0:0:1`:

1. **Did you restart the application?**
   - The code changes require a restart
   - Stop and start Spring Boot again

2. **Check Spring Boot Console:**
   - Look for any errors
   - The IP extraction should happen without errors

3. **Verify Header is Being Received:**
   - Add temporary logging (see FIX_IP_HEADER_NOT_WORKING.md)
   - Check if `X-Forwarded-For` appears in Spring Boot logs

4. **Test with Different Username:**
   - Try with `testuser_argon2` instead of `admin`
   - Check if the IP is saved correctly

## 📊 What to Share

If you want help debugging, share:
1. The result of the SQL queries above
2. Any errors from Spring Boot console
3. Whether you restarted the application after code changes
