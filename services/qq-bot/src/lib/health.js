import http from "node:http";

export function startHealthServer(config, state) {
  const server = http.createServer((req, res) => {
    if (req.url !== "/health") {
      res.writeHead(404, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "not_found" }));
      return;
    }

    const body = {
      ok: true,
      enabled: config.enabled,
      ready: !!state.ready,
      transport: config.transport,
      lastMessageAt: state.lastMessageAt || null,
      lastError: state.lastError || null,
    };

    res.writeHead(200, {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    });
    res.end(JSON.stringify(body));
  });

  server.listen(config.healthPort, "127.0.0.1");
  return server;
}
