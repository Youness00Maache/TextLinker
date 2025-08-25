// Server code diff for /upload-chunk endpoint (Node.js/Express)
// Add this to your existing Express server

const express = require('express');
const app = express();

// Configure body parser with increased limit for chunks
app.use(express.json({ limit: '1mb' }));

// CORS headers if needed for cross-origin requests
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

// In-memory chunk storage: { [token]: { total, parts: Map<index,string>, createdAt } }
const chunkAssemblies = new Map();
const CHUNK_TTL_MS = 30 * 60 * 1000; // 30 minutes

function maskToken(token) {
    return token && token.length > 6 ? token.slice(0, 3) + '***' + token.slice(-3) : '***';
}

// Cleanup expired assemblies
setInterval(() => {
    const now = Date.now();
    for (const [token, assembly] of chunkAssemblies) {
        if (now - assembly.createdAt > CHUNK_TTL_MS) {
            console.log(`[cleanup] Removing expired chunks for token=${maskToken(token)}`);
            chunkAssemblies.delete(token);
        }
    }
}, 60 * 1000);

// NEW ENDPOINT: POST /upload-chunk
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
        console.log(`[upload-chunk] Invalid chunk index: ${chunkIndex} >= ${totalChunks}`);
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
        console.log(`[upload-chunk] Created new assembly for token=${masked} total=${totalChunks}`);
    }

    // Validate totalChunks consistency
    if (assembly.total !== totalChunks) {
        console.log(`[upload-chunk] Total chunks mismatch: expected=${assembly.total} got=${totalChunks}`);
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
                console.log(`[upload-chunk] Missing chunk ${i} during assembly`);
                return res.status(409).json({ ok: false, error: `Missing chunk ${i}` });
            }
            fullText += assembly.parts.get(i);
        }

        // Clean up stored chunks
        chunkAssemblies.delete(token);
        
        console.log(`[upload-chunk] ASSEMBLED token=${masked} totalLen=${fullText.length} at=${new Date().toISOString()}`);

        // Process exactly like existing /upload does
        // TODO: Replace this with your actual upload processing logic
        // For example: save to database, broadcast via WebSocket, etc.
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

// Placeholder for your existing upload processing logic
function processUploadedText(token, text) {
    console.log(`[processUpload] token=${maskToken(token)} len=${text.length} prefix='${text.slice(0, 50)}'`);
    // TODO: Implement your actual logic here:
    // - Save to database
    // - Broadcast via WebSocket
    // - Whatever your existing /upload endpoint does
}

// Keep your existing /upload endpoint unchanged
app.post('/upload', (req, res) => {
    const { token, text } = req.body || {};
    if (!token || typeof text !== 'string') {
        return res.status(400).json({ ok: false, error: 'Invalid parameters' });
    }
    
    console.log(`[upload] token=${maskToken(token)} len=${text.length}`);
    processUploadedText(token, text);
    res.status(200).json({ ok: true });
});

module.exports = app;
