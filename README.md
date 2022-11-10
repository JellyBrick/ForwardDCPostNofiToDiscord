# ForwardDCPostNofiToDiscord

Forward DCInside new post notification to discord using webhook

## Setting (setting.json)

```json
{
  "galleryId": "Gallery ID, mini gallery -> mi$galleryId, others -> galleryId",
  "cron": "6 fields Cron expression, (example: 0 0 * * * ? *)",
  "webhookUrl": "Discord Webhook URL",
  "headIds": [head-text-id (int)],
  "breakpointMode": reading post from after breakpoint (boolean, optional, default: true),
  "useCache": enable HTTP cache (boolean, optional, default: false),
  "cacheDirectory": cache directory path (string, optional, default: "path"),
  "cacheSize": maximum cache size (long, byte unit, optional, default: -1)
}
```
