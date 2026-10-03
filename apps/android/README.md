# Android Mobile Assistant

Android client shared by WeChat and QQ mobile chat scenarios.

## User flow

1. User copies or shares conversation text.
2. LULU calls the backend conversation-coach API.
3. A/B/C suggestions appear in an Android overlay.
4. Tapping a suggestion copies it to the clipboard.
5. The user decides whether to paste and send it.

The app never hooks the chat app and never sends a message automatically.

See `../../docs/channels/ANDROID_CHAT_ASSISTANT.md` for the product design.
