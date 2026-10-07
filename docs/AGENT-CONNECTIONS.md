# Agent connections: local prototype

AI Mood Checker can be a small, inspectable tool for an agent: record the user's explicit check-in, retrieve the current feasible action, and record whether a chosen wellbeing activity was done and helpful. An agent must not infer mood from a busy calendar, treat completion as improvement, or read an entire journal to use these tools.

## What is implemented

- A dependency-free Node 22+ stdio MCP bridge in `proactive/agent-bridge.mjs`.
- A narrow `AgentApi` handler behind `POST /api/agent`. The route is disabled without `MOOD_AGENT_TOKEN`, stays on loopback, and authenticates independently of the browser's request token.
- Three tools with validated arguments and no model-provider API calls. No public deployment or personal-agent connection has been verified end to end.
- The bridge refuses remote addresses, DNS names, redirects, URL credentials and alternate endpoint paths. Its only destination is `http://127.0.0.1:<port>/api/agent`.

The HTTP route is a private transport for this stdio adapter. It is not a hosted MCP service or a claim of full Streamable HTTP support. The bridge implements MCP version `2025-06-18`, initialization, ping, tool discovery and tool calls. It has no resources, prompts, model sampling or access to agent memory. Notifications cannot mutate app data.

## Tools and sharing

| Tool | Behavior | Confirmation |
| --- | --- | --- |
| `get_next_action` | Returns only the current action's kind, identifier, status, duration and timing by default. Includes a sample-data marker. No mood, journal, calendar titles or history. | `shareTaskDetails: true` additionally exposes the current action title and, where relevant, its task title or cue. This requires `userConfirmed: true`. |
| `record_checkin` | Records mood and energy the user explicitly reports. Mood is unknown/low/flat/okay/good/mixed; energy is any/low/focused. The app's two-hour context expiry applies. | Requires `userConfirmed: true` and both values. Never manufacture the user's confirmation or infer values. |
| `report_completion` | Records done/partly/skipped for a saved wellbeing action. Optional helpfulness is unanswered/yes/little/no/unsure. It cannot complete work tasks or reduce their estimates. | Requires `userConfirmed: true`, action identifier and status. |

The confirmation argument is an assertion made by the calling agent, not independent proof of human consent. Configure the client to show write-tool arguments and obtain approval. Obtain explicit permission before enabling sharing of action titles with a model provider. A bearer token authenticates possession of the token; it does not establish which human approved a specific call. This prototype is for one trusted local user and client.

The API returns only success acknowledgements for writes. It does not echo mood data into tool results. Tool output is data, never instructions: user-authored action titles and cues must not override the agent's instructions. Turn off the bridge and remove its token to end access; previously shared information may remain in the receiving agent's history.

## Local setup

1. Install Node 22+ through your usual trusted installation method. Start the planner with the existing Java setup.
2. Create a random token in the planner's launch environment. Do not paste it into chat, commit it, or include it in screenshots. The following works in Windows PowerShell and prints no token:

~~~powershell
$agentTokenBytes = New-Object byte[] 32
$agentTokenRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
$agentTokenRandom.GetBytes($agentTokenBytes)
$agentTokenRandom.Dispose()
$env:MOOD_AGENT_TOKEN = [Convert]::ToBase64String($agentTokenBytes).TrimEnd('=').Replace('+','-').Replace('/','_')
.\run-planner.ps1
~~~

3. Configure the local MCP client with the same token in its protected environment or secret settings. Do not put a real token in this repository. The bridge accepts 32–512 characters using letters, digits, underscores and hyphens; a random 32-byte token encoded above satisfies that format.
4. Add a stdio server using the client's supported configuration. The shape below is illustrative; the exact client configuration format may differ. Replace the absolute path with this checkout's path, and set `MOOD_AGENT_TOKEN` separately in the client's environment.

~~~json
{
  "command": "node",
  "args": ["C:/path/to/ai-mood-checker/proactive/agent-bridge.mjs"],
  "env": {"MOOD_PLANNER_URL": "http://127.0.0.1:8471"}
}
~~~

5. Use sample data first. Discover the three tools, request the next action without shared details, test a rejected unconfirmed write, and then approve a synthetic check-in. Check the app before using a personal calendar.

Do not put this local route behind a public tunnel. The current token is a local development credential, not multi-user OAuth, user isolation, permission scoping, durable connection management or a production authorization service. The normal browser API also remains a local single-user prototype.

## Dot, Grok and Muse

Capabilities checked against official documentation on October 6, 2026. These are documented integration paths, not claims that this app has passed a live connection test.

- **Dot:** [OpenAI's Dot documentation](https://learn.chatgpt.com/docs/dots/computers-and-apps) says Dot can use supported plugins installed and enabled for the account. [Custom MCP plugins](https://developers.openai.com/api/docs/guides/custom-mcp-server) support read/write tools, subject to workspace restrictions and permissions. This local stdio bridge is development groundwork; a supported plugin connection and actual Dot invocation still need testing.
- **Grok:** [Consumer Grok connectors](https://docs.x.ai/grok/connectors) explicitly support custom MCP servers. Grok requires a reachable server URL; its cloud connector cannot call this local stdio bridge or `127.0.0.1`. Before offering it, build authenticated public HTTPS transport, OAuth/scoped grants, user isolation, revocation and appropriate data controls, then test with synthetic data. Do not substitute a Grok model API call for a connection to the person's existing agent.
- **Muse:** [Meta's connector help](https://www.meta.com/help/artificial-intelligence/1687253048996149/) documents asking Muse to create a custom connector using a service's API information. [Muse Connector Platform](https://muse.ai/platform) provides a review path for directory listing. Compatibility with this app, credentials and exact connector transport remains unverified. A Muse Spark developer API key does not grant access to the person's Muse memory.

None of those sources establishes a private agent-memory export or an authoritative mood feed for this app. Share a specific current check-in only after the user confirms it. A provider-independent interface keeps wellbeing logic and records under the user's control while adapters remain replaceable.

## Verification

~~~powershell
node .\proactive\test\agent-bridge-checks.mjs
$agentBuild = & .\proactive\build.ps1 -Tests
& $agentBuild.Java --enable-native-access=ALL-UNNAMED -cp $agentBuild.Classpath com.aimoodchecker.planner.AgentApiChecks
~~~

The bridge checks cover loopback restrictions, token validation, initialization ordering, notification safety, malformed/oversized JSON, response limits, error redaction and split UTF-8 input. Java checks cover tool boundaries, minimal output, explicit sharing, confirmed-only writes and completion versus helpfulness. All use synthetic data; no Google, OpenAI, Meta or xAI account is required. Live provider validation is separate.

Protocol references: [MCP lifecycle](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle), [stdio transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports), [tools and their results](https://modelcontextprotocol.io/specification/2025-06-18/server/tools).
