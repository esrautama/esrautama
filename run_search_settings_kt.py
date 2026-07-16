with open('app/src/main/java/com/example/ui/SfaApp.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for idx, line in enumerate(lines):
    if 'showUrlSettings' in line:
        print(f"Line {idx+1}: {line.strip()}")
