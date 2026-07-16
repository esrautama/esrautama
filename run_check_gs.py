with open('AppsScript.gs', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for idx in range(1705, 1728):
    if idx < len(lines):
        print(f"{idx+1}: {lines[idx].strip()}")
