with open('index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

login_line = -1
for idx, line in enumerate(lines):
    if 'id="loginScreen"' in line:
        login_line = idx
        break

results = []
if login_line != -1:
    results.append(f"Found loginScreen on line {login_line+1}\n")
    for idx in range(login_line, min(login_line + 80, len(lines))):
        results.append(f"{idx+1}: {lines[idx]}")

with open('index_login_snippet.txt', 'w', encoding='utf-8') as f_out:
    f_out.writelines(results)
print("Done index_login_snippet")
