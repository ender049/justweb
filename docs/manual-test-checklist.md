# Manual Test Checklist

## Core Flow

1. Add a website tool from the main screen.
2. Open the website from the list.
3. Add the website to the launcher.
4. Open it from the launcher icon.
5. Re-open the same launcher icon and confirm it reuses or updates the existing task.

## Editing

1. Edit an existing website.
2. Change fullscreen mode and confirm the runtime page updates after reopening.
3. Change keep-screen-on and confirm the page does not sleep while open.
4. Change desktop mode and confirm desktop/mobile layout changes on a compatible site.
5. Change external link policy and confirm behavior matches the selected option.

## Web Compatibility

1. Log in on a normal website.
2. Run an OAuth or popup-based login flow.
3. Upload a file from a website form.
4. Download a normal file.
5. Download a `blob:` file and confirm the save dialog appears.
6. Open a same-domain link.
7. Open a subdomain link.
8. Open an external-domain link.

## Permissions

1. Test camera permission from a webpage.
2. Test microphone permission from a webpage.
3. Test geolocation permission from a webpage.
4. Deny each permission once and confirm the website fails gracefully.

## Recovery

1. Use "Reload" and confirm the configured URL reloads.
2. Use "Clear login" and confirm the site requires login again.
3. Use "Reset data" and confirm cached page assets are refreshed.

## Shortcuts

1. Add the same site to the launcher twice and confirm it updates instead of creating duplicates on supported launchers.
2. Delete a site and confirm the launcher shortcut is removed or disabled, depending on launcher behavior.

## Layout

1. Check the main list layout on a small phone.
2. Check the edit screen on a small phone.
3. Rotate the web page between portrait and landscape.
4. Confirm fullscreen remains stable after returning from background.
