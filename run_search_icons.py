with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r', encoding='utf-8') as f:
    content = f.read()

import re
# Find all occurrences of Icons.Default.Settings, Icons.Default.Build, etc in the file
matches = re.finditer(r'Icons\.Default\.\w+', content)
for m in matches:
    # Print match and line number
    line_no = content[:m.start()].count('\n') + 1
    print(f"Line {line_no}: {m.group()}")
