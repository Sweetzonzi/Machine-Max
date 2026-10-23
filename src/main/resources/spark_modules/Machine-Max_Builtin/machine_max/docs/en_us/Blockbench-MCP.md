# Driving Blockbench from an AI client with MCP

This page shows how to enable the MCP (Model Context Protocol) server in the Blockbench desktop app so that a compatible AI client can read the scene, edit the model and export it. Rules for writing content-pack JSON live in the other pages of this folder.

- Plugin repository: <https://github.com/jasonjgardner/blockbench-mcp-plugin>
- Plugin file: <https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js>

---

## 1. Install

1. Open the **desktop** build of Blockbench. The web build has no local process and no listening port, so the plugin cannot run there.
2. `File > Plugins`, click **Load Plugin from URL** and paste the plugin file URL above.
3. Grant the `net` and `process` permissions it asks for.

The plugin starts an HTTP MCP server inside the editor process. Port and path are configured under `Settings > General`.

---

## 2. Connection settings

| Setting | Default | Meaning |
| --- | --- | --- |
| MCP Server Port | `3000` | Listening port |
| MCP Server Endpoint | `/bb-mcp` | Full URL is `http://localhost:3000/bb-mcp` |
| MCP Server Host | `127.0.0.1` | Change to `0.0.0.0` or `::` to accept connections from other machines |
| Enable risky_eval | checked | Lets a client run arbitrary JavaScript inside the editor; when unchecked, the tool is not offered |

Point the client at `http://localhost:3000/bb-mcp` with the **Streamable HTTP** transport. Per-client configuration snippets are in the plugin repository README.

---

## 3. What a client can do

- Read: project structure, outliner, current selection, cube UVs, materials and textures.
- Edit: place and modify cubes and groups, mesh editing, box and mesh UVs, textures and PBR materials, particles, animation and bones.
- Look: render screenshots from offscreen views at several angles without moving your own camera.
- Export: run Blockbench's own export formats directly (Bedrock entity, Java block/item, and so on).

---

## 4. Security notes

The server listens on loopback only and rejects requests whose `Origin`/`Host` is not local. It has **no authentication** though: any local process that can reach the port may call every tool, including `risky_eval`. Do not expose it to the internet, and uncheck `Enable risky_eval` on machines you do not trust.

---

## 5. Without the editor running

The plugin repository also ships a stdio headless server that reads and writes `.bbmodel` files without starting Blockbench:

```bash
npx -y github:jasonjgardner/blockbench-mcp-plugin --root ./models
```

`--root` is required and confines file access to that directory. Useful for batch validation and batch rendering.
