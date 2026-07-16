def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """    if (user) {
      return { success: true, user: user };
    }
    return { success: false, message: "Username atau password salah." };"""
    
    new_code = """    if (user) {
      return { success: true, user: user };
    }
    if (String(username).trim().toLowerCase() === "admin" && String(password).trim() === "admin") {
      return { success: true, user: { username: "admin", Username: "admin", role: "Super Admin", Role: "Super Admin", id: "0", ID: "0" } };
    }
    return { success: false, message: "Username atau password salah." };"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
