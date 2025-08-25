// Server code for unread web messages feature
const express = require('express');
const app = express();
app.use(express.json({ limit: '1mb' }));

// CORS headers
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

// Storage model: { token: [ {id, origin, text, created_at, consumed} ] }
const messageStore = {};

function maskToken(token) {
    return token && token.length > 6 ? token.slice(0, 3) + '***' + token.slice(-3) : '***';
}

function generateId() {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

// Modified /upload endpoint - handles both app and web uploads
app.post('/upload', (req, res) => {
    const { token, text, origin } = req.body;
    
    if (!token || typeof token !== 'string') {
        return res.status(400).json({ ok: false, error: 'Invalid token' });
    }
    
    if (!text || typeof text !== 'string') {
        return res.status(400).json({ ok: false, error: 'Invalid text' });
    }
    
    if (text.length > 200000) {
        return res.status(413).json({ ok: false, error: 'Text too large' });
    }
    
    const messageOrigin = origin || 'app'; // default to app if not specified
    const item = {
        id: generateId(),
        origin: messageOrigin,
        text: text,
        created_at: new Date().toISOString(),
        consumed: false
    };
    
    if (!messageStore[token]) {
        messageStore[token] = [];
    }
    messageStore[token].push(item);
    
    console.log(`[upload] token=${maskToken(token)} origin=${messageOrigin} len=${text.length} id=${item.id}`);
    
    // Process/broadcast same as before (your existing logic here)
    
    res.json({ ok: true });
});

// New /upload-web endpoint - explicitly for website posts
app.post('/upload-web', (req, res) => {
    const { token, text } = req.body;
    
    if (!token || typeof token !== 'string') {
        return res.status(400).json({ ok: false, error: 'Invalid token' });
    }
    
    if (!text || typeof text !== 'string') {
        return res.status(400).json({ ok: false, error: 'Invalid text' });
    }
    
    if (text.length > 200000) {
        return res.status(413).json({ ok: false, error: 'Text too large' });
    }
    
    const item = {
        id: generateId(),
        origin: 'web',
        text: text,
        created_at: new Date().toISOString(),
        consumed: false
    };
    
    if (!messageStore[token]) {
        messageStore[token] = [];
    }
    messageStore[token].push(item);
    
    console.log(`[upload-web] token=${maskToken(token)} len=${text.length} id=${item.id}`);
    
    res.json({ ok: true });
});

// New endpoint: get unread web-origin messages and mark consumed
app.get('/text/:token/unread-web', (req, res) => {
    const token = req.params.token;
    
    if (!token || typeof token !== 'string') {
        return res.status(400).json({ ok: false, error: 'Invalid token' });
    }
    
    const messages = messageStore[token] || [];
    const unreadWeb = messages.filter(msg => msg.origin === 'web' && !msg.consumed);
    
    // Sort by created_at ascending
    unreadWeb.sort((a, b) => new Date(a.created_at) - new Date(b.created_at));
    
    // Mark as consumed
    const consumedIds = [];
    unreadWeb.forEach(msg => {
        msg.consumed = true;
        consumedIds.push(msg.id);
    });
    
    console.log(`[unread-web] token=${maskToken(token)} found=${unreadWeb.length} consumed=[${consumedIds.join(',')}]`);
    
    // Return minimal fields
    const response = {
        ok: true,
        messages: unreadWeb.map(msg => ({
            id: msg.id,
            text: msg.text,
            created_at: msg.created_at
        }))
    };
    
    res.json(response);
});

// Keep existing /text/{token} endpoint for backward compatibility
app.get('/text/:token', (req, res) => {
    const token = req.params.token;
    
    if (!token) {
        return res.status(400).json({ ok: false, error: 'Invalid token' });
    }
    
    const messages = messageStore[token] || [];
    
    // Return the latest message (old behavior)
    if (messages.length === 0) {
        return res.status(404).json({ ok: false, error: 'No text found' });
    }
    
    const latest = messages[messages.length - 1];
    console.log(`[text-legacy] token=${maskToken(token)} returning latest id=${latest.id}`);
    
    res.json({ text: latest.text });
});

module.exports = app;
