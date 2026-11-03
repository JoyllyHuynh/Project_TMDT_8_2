# ComicWorm chatbot

The chatbot uses Gemini through Google's OpenAI-compatible Chat Completions endpoint. This keeps the current conversation and function-calling flow while using a Gemini model. The API key is read only by the backend and is never sent to the browser. Without a key, the app still starts and returns a clearly labeled FAQ/support fallback.

## What it can do

- Answer Vietnamese questions grounded in the verified FAQ knowledge base.
- Search the public catalog, filter products, and return current prices and stock.
- Show product details and available categories.
- Look up up to five recent orders belonging to the authenticated user. It does not expose addresses, phone numbers, notes, payment credentials, or tokens.
- Return relevant product cards and website links alongside its answer.

The chatbot cannot modify carts or perform purchases, payments, or cancellations. Conversation history is held only in the open page's memory; no chatbot database tables or migrations are used. The widget warns users not to submit passwords, one-time codes, addresses, or payment details.

## Configuration

Create a Gemini API key in Google AI Studio and set these environment variables before starting the backend:

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `GEMINI_API_KEY` | For AI answers | Empty | Gemini API key; keep it out of source control |
| `CHATBOT_API_BASE_URL` | No | `https://generativelanguage.googleapis.com/v1beta/openai` | Gemini OpenAI-compatible API base URL |
| `CHATBOT_MODEL` | No | `gemini-2.5-flash` | Gemini model |
| `CHATBOT_TIMEOUT_SECONDS` | No | `15` | Request timeout, bounded to 1–30 seconds |

PowerShell example:

```powershell
$env:GEMINI_API_KEY = "<your-gemini-api-key>"
$env:CHATBOT_MODEL = "gemini-2.5-flash"
.\mvnw.cmd spring-boot:run
```

The verified FAQ knowledge is maintained in `src/main/resources/chatbot/knowledge-base.json`. Add only facts confirmed in the current website or backend behavior; do not state unverified shipping, refund, or payment policies.

## API

`POST /api/chatbot/message` is public for product/catalog and FAQ questions. The optional `history` accepts up to eight `USER`/`ASSISTANT` messages and is not persisted.

```json
{
  "message": "Tìm Naruto giá dưới 80000",
  "history": []
}
```

The response follows the standard ComicWorm `ApiResponse` envelope. Its `data` contains `answer`, `mode` (`ai`, `unconfigured`, or `unavailable`), `requiresLogin`, public product cards, and verified site links. Product prices and stock come from the public catalog service, which filters to active, approved, non-deleted products.

Order lookup is a read-only model tool with no user-ID argument. The backend derives the buyer from the authenticated JWT and returns at most five recent orders with a limited item summary.
Messages and bounded tool results needed to answer them are sent to Gemini.
