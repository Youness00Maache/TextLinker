# Server Debug Request

The domain https://textlinker.pro returns "Cannot POST /upload-chunk" (404). Please:

1. **Restart the Node process** hosting the app
2. **Add this temporary route-list snippet** to server.js (after routes, before app.listen):
```javascript
console.log('Registered routes:');
app._router.stack.forEach(function(r){
  if (r.route && r.route.path) {
    console.log(Object.keys(r.route.methods).join(','), r.route.path);
  }
});
```

3. **Restart** so we can confirm `post /upload-chunk` is registered

4. **From the server**, run:
```bash
curl -i -X POST http://127.0.0.1:3000/upload-chunk \
  -H "Content-Type: application/json" \
  -d '{"token":"DEMO","chunkIndex":0,"totalChunks":1,"textChunk":"hello"}'
```

5. **Paste the curl output** and **last 20 lines of server logs**

6. **Verify nginx/apache** proxies `/upload-chunk` to the Node backend and check for any route prefix like `/api`

Expected curl response: `200 {"ok":true,"receivedIndex":0,"assembled":true}`
