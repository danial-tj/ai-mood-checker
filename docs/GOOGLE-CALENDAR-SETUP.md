# Connect Google Calendar to AI Mood Checker

For the local prototype on this computer. The app reads events from your primary calendar to avoid scheduling over commitments. It does not edit your calendar. Real account setup is still pending verification.

## 1. Enable Calendar

In [Google Cloud](https://console.cloud.google.com/), select a project using the picker at the top, or create one named **AI Mood Checker**. Keep this same project selected throughout setup. Open **APIs & Services → Library**, find **Google Calendar API**, and choose **Enable**.

## 2. Set up the consent screen

Open **Google Auth platform → Branding → Get started**. Use **AI Mood Checker** for the app name and your email for support/contact details. For a personal Google account, choose **External** as the audience. Review Google's policy before agreeing and creating the configuration.

In **Audience**, keep the app in **Testing**, then add the Google account whose calendar you want to connect under **Test users**.

In **Data Access → Add or Remove Scopes**, add only:

`https://www.googleapis.com/auth/calendar.events.readonly`

Save the selection. These steps follow Google's [consent-screen guide](https://developers.google.com/workspace/guides/configure-oauth-consent).

## 3. Download the connection file

Open **Google Auth platform → Clients → Create client**. Select **Desktop app**, name it **AI Mood Checker Local**, and create it. Download its JSON file. Keep it outside the project folder; its existing filename is fine.

Give Codex **only the full local file path**, for example `C:\Users\Danial\Downloads\client_secret_...json`. Do not paste its contents. The Desktop client type is required for this app's local callback. Google's [Calendar setup guide](https://developers.google.com/workspace/calendar/api/quickstart/java) documents the API and Desktop client steps.

## 4. Connect from the app

Codex can validate the file and restart the local app with it. If doing this yourself, run from the repository folder, replacing the example path:

```powershell
.\setup-google-calendar.ps1 -CredentialsFile 'C:\path\to\your-downloaded-file.json' -CheckOnly
.\setup-google-calendar.ps1 -CredentialsFile 'C:\path\to\your-downloaded-file.json'
```

The helper prints no credential values. Stop the existing preview before launching on the same port. In [the local app](http://127.0.0.1:8471/#connections), choose **Connections**. If still using the sample, export any changes you want to keep before choosing **Start my own plan**. Then choose **Connect Google Calendar** and review the requested read-only event access.

Check the successful sync time and event times. A completed sign-in alone does not verify the connection. To test conflicts, use an event you create for testing, move it over a reserved action, and choose **Sync now**. The action should need review. This app cannot move or delete the event for you.

Only the primary calendar is imported. Tokens stay in memory, so restarting the app requires reconnecting. The prototype is local to this computer; phone access and durable phone notifications are separate work.

## If Google refuses access

- **Not an allowed test user:** check Audience and add the exact account used for sign-in.
- **API disabled:** enable Calendar in the same project as the downloaded client.
- **Wrong client type:** download a Desktop app client rather than a Web or service-account file.
- **App works but calendar is empty:** confirm events are on the primary calendar and within the app's window of yesterday through the next 14 days.

Share the error message without authorization codes, tokens or credential contents if setup fails.
