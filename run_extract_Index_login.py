with open('Index.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

login_line = -1
for idx, line in enumerate(lines):
    if 'id="loginScreen"' in line:
        login_line = idx
        break

results = []
if login_line != -1:
    results.append(f"Found loginScreen div on line {login_line+1} in Index.html\n")
    for idx in range(login_line, min(login_line + 60, len(lines))):
        results.append(f"{idx+1}: {lines[idx]}")
else:
    results.append("loginScreen div not found in Index.html\n")

with open('Index_login_snippet_file.txt', 'w', encoding='utf-8') as f_out:
    f_out.writelines(results)
print("Done Index_login_snippet")
