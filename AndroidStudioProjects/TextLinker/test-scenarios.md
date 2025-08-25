# Test Scenarios for Upload/Refresh Flow

## Test 1: Normal Upload Success
**Steps:**
1. Create 2-3 notes in app
2. Scan QR code with token "TEST1"
3. Wait for upload to complete

**Expected Logs:**
```
ScanFragment: Token scanned: previous='null' new='TEST1'
ScanFragment: Saved token and timestamp to prefs
ScanFragment: uploadAllNotesToServer: token=TES***ST1 len=XXX prefix='Title: "Note 1"'
ScanFragment: Set upload_in_progress_TEST1 = true
ScanFragment: Upload response: success=true code=200 body='{"ok":true}'
ScanFragment: Upload succeeded: wrote last_uploaded_payload_TEST1, cleared upload_in_progress
```

## Test 2: Upload 413 Error (Payload Too Large)
**Steps:**
1. Create many large notes (>1MB total)
2. Scan QR code
3. Observe 413 response

**Expected Logs:**
```
ScanFragment: Set upload_in_progress_<token> = true
ScanFragment: Upload response: success=false code=413 body='Request Entity Too Large'
ScanFragment: Upload failed 413 (too large): cleared upload_in_progress, did not write last_uploaded_payload
```

## Test 3: Refresh Blocked During Upload
**Steps:**
1. Start upload (don't wait for completion)
2. Immediately pull-to-refresh on FirstFragment
3. Should see "Upload in progress" message

**Expected Logs:**
```
ScanFragment: Set upload_in_progress_<token> = true
FirstFragment: Refresh blocked: upload_in_progress_<token> = true
```

## Test 4: Echo Suppression Works
**Steps:**
1. Upload notes successfully
2. Pull-to-refresh after upload completes
3. Should see "echo suppressed" message

**Expected Logs:**
```
FirstFragment: Starting refresh for token (masked)
FirstFragment: Fetched text len=XXX, lastUploaded len=XXX
FirstFragment: Echo suppressed: fetched text matches last_uploaded_payload
```

## Test 5: Token Change Clears Previous Data
**Steps:**
1. Upload with token "TEST1"
2. Scan new QR with token "TEST2"
3. Check that previous token data is cleared

**Expected Logs:**
```
ScanFragment: Token scanned: previous='TEST1' new='TEST2'
ScanFragment: Token changed: cleared prefs for previous token
ScanFragment: Saved token and timestamp to prefs
```

## What to Share
For each test, share:
1. The relevant log lines (filter by "ScanFragment" and "FirstFragment")
2. Toast messages shown to user
3. Any error messages or unexpected behavior
