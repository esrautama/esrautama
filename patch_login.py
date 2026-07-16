def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """const user = userList.find(u => String(u.username).trim() === String(username).trim() && String(u.password).trim() === String(password).trim());"""
    new_code = """const user = userList.find(u => String(u.username || u.Username).trim() === String(username).trim() && String(u.password || u.Password).trim() === String(password).trim());"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
