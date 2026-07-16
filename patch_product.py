def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old = """function saveProduct(id, name, retail, wholesale, stock) {
  try {
    if (!id || id.trim() === '') id = "PRD-" + new Date().getTime();
    const ms = new MasterService();
    let sheet = ms.getSheet("Products");
    if (!sheet) { 
       sheet = ms.insertSheet("Products"); 
       sheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);
    }"""
    
    new = """function saveProduct(id, name, retail, wholesale, stock) {
  try {
    if (!id || id.trim() === '') id = "PRD-" + new Date().getTime();
    const ms = new MasterService();
    let sheet = ms.getSheet("Products");
    if (!sheet) { 
       sheet = ms.insertSheet("Products"); 
       sheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);
    }
    
    let lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 2).getValues();
      for(let i=0; i<data.length; i++) {
        let isExisting = id && String(data[i][0]) === String(id);
        if (isExisting) continue;
        if (String(data[i][1]).trim().toLowerCase() === String(name).trim().toLowerCase()) {
          return { success: false, message: "Gagal: Nama produk '" + name + "' sudah ada (duplikat)." };
        }
      }
    }"""

    if old in content:
        content = content.replace(old, new)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to patch {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
