def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """function saveProduct(id, name, retail, wholesale, stock) {
  try {
    const ms = new MasterService();
    let sheet = ms.getSheet("Products");
    if (!sheet) {
       sheet = ms.insertSheet("Products");
       sheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);
    }
    
    let lastRow = sheet.getLastRow();
    let isUpdate = false;"""
    
    new_code = """function saveProduct(id, name, retail, wholesale, stock) {
  try {
    if (!id || id.trim() === '') id = "PRD-" + new Date().getTime();
    const ms = new MasterService();
    let sheet = ms.getSheet("Products");
    if (!sheet) {
       sheet = ms.insertSheet("Products");
       sheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);
    }
    
    let lastRow = sheet.getLastRow();
    let isUpdate = false;"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
