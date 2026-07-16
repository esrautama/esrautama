def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code_user = """    const sheet = ms.getSheet("Users");
    if (!sheet) return { success: false, message: "Sheet Users tidak ditemukan" };"""
    new_code_user = """    let sheet = ms.getSheet("Users");
    if (!sheet) {
       sheet = ms.insertSheet("Users");
       sheet.appendRow(["ID", "Username", "Password", "Role"]);
    }"""
    
    old_code_prod = """    const sheet = ms.getSheet("Products");
    if (!sheet) return { success: false, message: "Sheet Products tidak ditemukan" };"""
    new_code_prod = """    let sheet = ms.getSheet("Products");
    if (!sheet) {
       sheet = ms.insertSheet("Products");
       sheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);
    }"""

    if old_code_user in content:
        content = content.replace(old_code_user, new_code_user)
    if old_code_prod in content:
        content = content.replace(old_code_prod, new_code_prod)

    with open(file_name, "w") as f:
        f.write(content)
    print(f"Patched {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
