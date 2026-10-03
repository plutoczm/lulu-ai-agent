# Image Search MCP Service

Standalone Spring Boot MCP server that exposes image-search capability to the backend.

It is optional and is only required when `MCP_ENABLED=true`.

## Build

From repository root:

```powershell
.\mvnw.cmd -f services\image-search-mcp\pom.xml -DskipTests package
```

The backend expects the packaged jar under this module's `target/` directory.
