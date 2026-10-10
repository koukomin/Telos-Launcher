# Crash Reporter

When the launcher crashes, a notification is posted. When you tap on that notification, the crash reporter screen opens. You can also navigate to that screen like this: Settings > Debug > Crash reporter.

The crash reporter lists crashes and exceptions of the last 7 days. When there are none, it shows an empty state. The detail screen of an entry has **Share**, **Report on GitHub** (crashes only) and **Delete**.

## Switching it off

The **Crash reporter** switch at the top of the screen (on by default) controls whether crashes and handled exceptions are saved on the device. Reports never leave the device and are not part of backups. When it is off, nothing is recorded and the app still behaves and crashes as usual. **Delete all reports** removes every saved report.

## Crashes

Crashes are marked with the <span class="material-symbols-rounded">error</span> icon. Crashes are unexpected errors that were not handled by launcher. They are often a consequence of bugs and should therefore be reported. You can click the <span class="material-symbols-rounded">bug_report</span> icon in the top right corner to create a new issue on GitHub. Make sure to fill in additional information like steps to reproduce (if possible) or what you were trying to do that lead to the crash.

[Read more about reporting bugs](/docs/contributor-guide/report-bugs).

## Exceptions

Exceptions are marked with the <span class="material-symbols-rounded">warning</span> icon. Exceptions are errors that were handled by the launcher. They can sometimes be helpful to locate bugs and other sources of errors, but as long as you don't notice anything strange, you can safely ignore them and do not need to report them. It is expected that some exceptions will occur while the launcher is running. For example, the most common source of exceptions is network timeouts due to the device being offline.
