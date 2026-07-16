import sys

with open("app/src/main/java/com/example/ui/SfaApp.kt", "r") as f:
    text = f.read()

import re
text = re.sub(r'(@Composable\s+){2,}', '@Composable\n', text)

with open("app/src/main/java/com/example/ui/SfaApp.kt", "w") as f:
    f.write(text)
