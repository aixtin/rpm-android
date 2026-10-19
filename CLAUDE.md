# rpm-android

Notes for AI coding assistants (and people) working in this repo. Read CONTRIBUTING.md first; its
rules on UI text, comments and commit sign-off apply to every change.

## This repo

The Android phone app (`phone`) and Android TV app (`tv`), sharing the `core` module, all in
`RPM.Android/`.

- Build, from `RPM.Android`: `./gradlew :phone:assembleDebug` or `:tv:assembleDebug`. Tests:
  `./gradlew test`.
- Playback runs on Media3. Player calls belong on the player's thread.
- Every TV screen must work with a D-pad and the Back key; check focus order when you add a control.
- `ffmpeg` is a copy of the AndroidX Media3 FFmpeg extension. Do not change its upstream code
  without a reason in the commit message.

## Comments

Comments are read by contributors who have not seen the history of the code, and many of them do
not have English as a first language. Write for them.

- Say what the code does and why, as it is now. Keep warnings about traps ("also set in
  `HomeView.xaml`, change both").
- No history: no dates, no names, no quotes from a conversation, no "used to", "was", "until", and
  no notes about code that has been removed. That belongs in the commit message.
- Plain international English: short sentences, common words, no idioms. Use British spelling
  (colour, centre, catalogue, licence, behaviour). No em-dashes.
- Keep identifiers as they are. Use the code's own names for things.

## Do not

- Do not put UI text in the code. Add it to `strings.json` in the `rpm` repo.
- Do not edit generated files (they say so in their first lines). Change the generator or its input.
- Do not add secrets, API keys or personal data to the repo.
