# Instructions for Server AI: Add /upload-chunk Endpoint

## Request
Add a new POST endpoint `/upload-chunk` to handle chunked file uploads for large payloads.

## Implementation Requirements

### 1. Body Parser Configuration
```javascript
app.use(express.json({ limit: '1mb' }));
```

### 2. CORS Headers (if cross-origin)
```javascript
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization');
    if (req.method === 'OPTIONS') {
        res.sendStatus(200);
    } else {
        next();
    }
});
```

### 3. Chunk Storage
```javascript
const chunkAssemblies = new Map();
const CHUNK_TTL_MS = 30 * 60 * 1000; // 30 minutes

function maskToken(token) {
    return token && token.length > 6 ? token.slice(0, 3) + '***' + token.slice(-3) : '***';
}

// Cleanup expired assemblies every minute
setInterval(() => {
    const now = Date.now();
    for (const [token, assembly] of chunkAssemblies) {
        if (now - assembly.createdAt > CHUNK_TTL_MS) {
            console.log(`[cleanup] Removing expired chunks for token=${maskToken(token)}`);
            chunkAssemblies.delete(token);
        }
    }
}, 60 * 1000);
```

### 4. Main Endpoint
```javascript
app.post('/upload-chunk', (req, res) => {
    const { token, chunkIndex, totalChunks, textChunk } = req.body || {};
    
    // Validate inputs
    if (!token || typeof token !== 'string' || 
        typeof chunkIndex !== 'number' || chunkIndex < 0 ||
        typeof totalChunks !== 'number' || totalChunks <= 0 ||
        typeof textChunk !== 'string') {
        console.log(`[upload-chunk] Bad request: token=${maskToken(token)} idx=${chunkIndex} total=${totalChunks}`);
        return res.status(400).json({ ok: false, error: 'Invalid request parameters' });
    }

    if (chunkIndex >= totalChunks) {
        return res.status(400).json({ ok: false, error: 'chunkIndex must be < totalChunks' });
    }

    const masked = maskToken(token);
    console.log(`[upload-chunk] token=${masked} idx=${chunkIndex}/${totalChunks} len=${textChunk.length}`);

    // Get or create assembly for this token
    let assembly = chunkAssemblies.get(token);
    if (!assembly) {
        assembly = { 
            total: totalChunks, 
            parts: new Map(), 
            createdAt: Date.now() 
        };
        chunkAssemblies.set(token, assembly);
    }

    // Validate totalChunks consistency
    if (assembly.total !== totalChunks) {
        return res.status(409).json({ ok: false, error: 'totalChunks mismatch with existing assembly' });
    }

    // Store the chunk
    assembly.parts.set(chunkIndex, textChunk);
    console.log(`[upload-chunk] Stored chunk ${chunkIndex}, assembly now has ${assembly.parts.size}/${assembly.total} parts`);

    // Check if we have all chunks
    if (assembly.parts.size === assembly.total) {
        // Assemble chunks in order
        let fullText = '';
        for (let i = 0; i < assembly.total; i++) {
            if (!assembly.parts.has(i)) {
                return res.status(409).json({ ok: false, error: `Missing chunk ${i}` });
            }
            fullText += assembly.parts.get(i);
        }

        // Clean up stored chunks
        chunkAssemblies.delete(token);
        
        console.log(`[upload-chunk] ASSEMBLED token=${masked} totalLen=${fullText.length} at=${new Date().toISOString()}`);

        // Process exactly like existing /upload does
        processUploadedText(token, fullText);

        return res.status(200).json({ 
            ok: true, 
            receivedIndex: chunkIndex, 
            assembled: true 
        });
    }

    // Partial assembly - just acknowledge this chunk
    res.status(200).json({ 
        ok: true, 
        receivedIndex: chunkIndex 
    });
});

// Replace this with your actual upload processing logic
function processUploadedText(token, text) {
    console.log(`[processUpload] token=${maskToken(token)} len=${text.length}`);
    // TODO: Call the same logic as your existing /upload endpoint
    // - Save to database
    // - Broadcast via WebSocket
    // - etc.
}
```

## Expected Behavior

### Request Format
```json
{
    "token": "abc123",
    "chunkIndex": 0,
    "totalChunks": 3,
    "textChunk": "This is chunk 0 content..."
}
```

### Response Format (partial)
```json
{
    "ok": true,
    "receivedIndex": 0
}
```

### Response Format (final assembly)
```json
{
    "ok": true,
    "receivedIndex": 2,
    "assembled": true
}
```

## Test Commands
```bash
# Single chunk
curl -X POST https://textlinker.pro/upload-chunk \
  -H "Content-Type: application/json" \
  -d '{"token":"DEMO","chunkIndex":0,"totalChunks":1,"textChunk":"hello"}'

# Multi-chunk (part 1)
curl -X POST https://textlinker.pro/upload-chunk \
  -H "Content-Type: application/json" \
  -d '{"token":"DEMO2","chunkIndex":0,"totalChunks":2,"textChunk":"first"}'

# Multi-chunk (part 2)  
curl -X POST https://textlinker.pro/upload-chunk \
  -H "Content-Type: application/json" \
  -d '{"token":"DEMO2","chunkIndex":1,"totalChunks":2,"textChunk":" second"}'
```

## Important Notes
- Keep existing `/upload` endpoint unchanged
- Process assembled text exactly like existing `/upload` does
- Clean up chunks after assembly or expiration
- Log all operations with masked tokens for security
- Return 400 for invalid requests, 409 for conflicts
