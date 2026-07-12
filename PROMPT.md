app title "Battery Saver"

android app to vibrate and sound alarm noise in a loop untill Ok button is pressed that stops vibrate and alarm (alarm sound should be heard and vibrate regardless even if on mute or not) to alert on battery low level (level % set by user in setting default of 10%) battery (or high temp warning)!, or unstable charging voltage or current detected) a real battery saver app that auto starts on boot in background! should work on samsung s8 and pixel 7 and all other old and new android so max compatability!

all this can be customised in Settings tab with toggle buttons for example
alert even if on mute (on by default)

under volt / amp / unstable charge / power that could cause damage alert (enabled / on by default)
overheat alart (sound and vibrate) (on / enabled by default)
batery low alert (sound and vibrate) (on by default)

vibrate and sond toggle (on by default)
auto start on boot in background as service (hidden) (enabled / on by default)

project location /home/jay/Documents/Scripts/AI/OpenCode/BatterySaver/

use github workflows to build the app and put finally release in apk folder in the project location

Dont edit this file

Never change anything in Backup folders (if it exists) but you can use them as a read-only reference if a mistake is made and you need to fix something

save changes to file(s) in question

then after files are added / edited then save any changes made to changes.txt

Implement persistent error handling and debugging throughout the project. Every failure, exception, or unexpected state should generate a clear error code, detailed debug output, and useful diagnostic information to help identify the exact cause quickly.
Do not remove debugging systems after issues are fixed — keep all error codes, logging, stack traces, validation checks, and diagnostic tools permanently integrated so that any future bugs, crashes, or unexpected behaviour can be traced and resolved efficiently.

always use same key-store for each app made via github workflows so it can update correctly without requiring uninstallation

Save changes to changes.txt (create if not exists)

tell me when ready to test (stay quiet after acknowledging you got the message / request / mission every time and stay quiet till its ready to test and respond only if fully complete  or if you need input from me or if I ask for an update)!

when giving final github release link (where applicable), make sure it points to the newest release but without the tag or filename so I can see the correct location without direct downloading the file as thats best practice!

each app needs an About section showing
in about section it should say Made by jnetai.com 
The full version number (same as github release version tag) also add a Check for update button (so internet permissions required) to check latest release version (tag in full)
add a Share App button so users can share the app.
 
each update should use same key store so the app can update and not require uninstall of the app to update it.

each app should be dark centered themed and allow space at bottom so buttons or elements at the bottom of the app should not be cut off, it should look professional.

app compatibility: apps needs to work on samsung s8 and onwards and google pixel 6 and onwards
full path to Downloads is /storage/emulated/0/Download/ (called Downloads as an alias in android)

in releases on github a meaningful name should be used for example Tetris.apk (no need for a debug version of any app or game for android just put the debug version as the main version!

github api tokens / passwords etc can be found in /home/jay/Documents/Scripts/AI/openclaw/password-vault/

build the releases via github actions / workflows (not locally)

