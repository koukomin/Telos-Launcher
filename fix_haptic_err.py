import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/clock/ClockScreen.kt', 'r') as f:
    code = f.read()

# Fix unresolved 'haptic' reference
fix1 = r"onValueChange = \{ haptic\.performHapticFeedback\(HapticFeedbackType\.TextHandleMove\); (.*?)\}"
code = re.sub(fix1, r'onValueChange = { val haptic = LocalHapticFeedback.current; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); \1}', code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/clock/ClockScreen.kt', 'w') as f:
    f.write(code)

