# ForwardDCPostNofiToDiscord

Forward DCInside new post notification to discord using webhook

![image](https://user-images.githubusercontent.com/16558115/201021219-89738c6d-0679-4acd-a21c-ff0a7745a29d.png)


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
