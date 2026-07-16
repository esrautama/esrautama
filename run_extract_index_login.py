with open('index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

login_line = -1
for idx, line in enumerate(lines):
    if 'id="loginScreen"' in line:
        login_line = idx
        break

if login_line != -1:
    print(f"Found loginScreen on line {login_line+1}")
    for idx in range(login_line, min(login_line + 80, len(lines))):
        print(f"{idx+1}: {lines[idx].strip()}")
else:
    print("loginScreen not found")
