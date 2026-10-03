# Services

Independently runnable processes live here.

- `backend/`: core Spring Boot API and the source of product business logic.
- `qq-bot/`: optional QQ official-bot channel adapter.
- `image-search-mcp/`: optional MCP tool server for image search.

Channel services should adapt external protocols and delegate product decisions to the backend.
