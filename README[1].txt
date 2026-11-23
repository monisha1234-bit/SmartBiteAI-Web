# SmartBite AI — browser version

This is a browser-based version of the supplied SmartBite AI project.
It keeps the menu, cart, AI chat, and AI food recommendations.

## Important
- The original Swing window cannot run directly as a normal website, so the interface is now HTML.
- AI requests are sent to a Java server. The Gemini key is read only from the server environment variable `GEMINI_API_KEY`.
- Never put your Gemini API key in `index.html`, source control, or a public file.

## Run locally (Java 17+)
From this folder, open PowerShell or a terminal:

```powershell
javac .\src\SmartBiteWeb.java
java -cp .\src SmartBiteWeb
```

The server reads `public/index.html` relative to the current folder, so run the command from this folder. Open http://localhost:8080.

Set the key privately in the same PowerShell window before running:
```powershell
$env:GEMINI_API_KEY="your-key"
```

## Deploy on Render (free web service)
1. Put this folder in a GitHub repository.
2. In Render, create **New > Web Service**, connect the repository, and choose the **Free** instance.
3. Select **Docker** as the runtime (Render detects the Dockerfile).
4. Add an environment variable named `GEMINI_API_KEY` and paste your key into Render's secret environment-variable field. Do not paste it into chat or source files.
5. Deploy. Render will provide an `onrender.com` URL after a successful deployment.

Free Render services sleep after 15 minutes without traffic and may take about a minute to wake up. They can also restart; this is suitable for a college demo, not a guaranteed always-on production service.
