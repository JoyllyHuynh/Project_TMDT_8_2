# ComicWorm chatbot

The chatbot uses a configurable OpenAI-compatible Chat Completions endpoint. Its API key is read only by the backend and is never sent to the browser. With no key, the app still starts and the endpoint returns a clearly labeled FAQ/support fallback.

## Configuration

Set these environment variables before starting the backend:

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `CHATBOT_API_KEY` | For AI answers | Empty | Provider API key; keep it out of source control |
| `CHATBOT_API_BASE_URL` | No | `https://api.openai.com/v1` | OpenAI-compatible API base URL |
| `CHATBOT_MODEL` | No | `gpt-4o-mini` | Chat-completions model |
| `CHATBOT_TIMEOUT_SECONDS` | No | `15` | Request timeout, bounded to 1–30 seconds |

PowerShell example:

```powershell
$env:CHATBOT_API_KEY = "<provider-key>"
$env:CHATBOT_MODEL = "gpt-4o-mini"
.\mvnw.cmd spring-boot:run
```

The verified FAQ knowledge is maintained in `src/main/resources/chatbot/knowledge-base.json`. Add only facts that are confirmed in the current website or backend behavior; do not state unverified shipping, refund, or payment policies.

## API

`POST /api/chatbot/message` is public for product/catalog and FAQ questions. The optional `history` accepts up to eight `USER`/`ASSISTANT` messages and is not persisted.

```json
{
  "message": "Tìm Naruto giá dưới 80000",
  "history": []
}
```

The response follows the standard ComicWorm `ApiResponse` envelope. Its `data` contains `answer`, `mode` (`ai`, `unconfigured`, or `unavailable`), `requiresLogin`, public product cards, and verified site links. Product prices and stock are fetched from the public catalog service, which filters to active, approved, non-deleted products.

Order lookup is a read-only model tool with no user-ID argument. The backend derives the buyer from the authenticated JWT and returns at most five recent orders with a limited item summary; recipient, address, phone, notes, payment credentials, and tokens are excluded. There is no chatbot action for cart changes, purchases, payments, or cancellations.

Conversation history exists only in the open page's memory. No chatbot tables, migrations, seeds, or database schema changes are required.
Messages and the bounded tool results needed to answer them are sent to the configured AI provider. The widget warns users not to submit passwords, one-time codes, addresses, or payment details.
