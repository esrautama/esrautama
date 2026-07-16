with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r', encoding='utf-8') as f:
    content = f.read()

import re
matches = re.finditer(r'(?i)audit|log\s*audit|sync\s*audit|riwayat\s*sinkronisasi|history\s*sync', content)
for m in matches:
    line_no = content[:m.start()].count('\n') + 1
    print(f"Line {line_no}: {content[m.start():m.end()]} -> {content[m.start()-40:m.end()+60].strip()}")
