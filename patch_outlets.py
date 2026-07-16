def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_handle = """                    o.id,
                    o.name,
                    o.type,
                    o.category,
                    o.address,"""
                    
    new_handle = """                    o.id,
                    o.name,
                    o.address,
                    o.type,
                    o.category,"""

    old_noo = """function saveOutletNOO(name, address, geo, day, salesName) {
  try {
    const ms = new MasterService();
    let sheet = ms.getSheet("Outlets");
    if (!sheet) return { success: false, message: "Sheet Outlets tidak ditemukan" };
    
    let lastRow = sheet.getLastRow();
    let newId = "NOO-" + new Date().getTime();
    
    sheet.appendRow([newId, name, "Regular", "Retail", address, geo, "", salesName]);
    
    // Also inject to today's route
    return injectOutletToSales(salesName, day, name);
  } catch(e) { return { success: false, message: e.toString() }; }
}"""

    new_noo = """function saveOutletNOO(name, address, geo, day, salesName) {
  try {
    const ms = new MasterService();
    let sheet = ms.getSheet("Outlets");
    if (!sheet) {
      sheet = ms.insertSheet("Outlets");
      sheet.appendRow(["ID", "Name", "Address", "Type", "PriceTier", "Geotag", "SalesID", "SalesName", "KodeHari"]);
    }
    
    let lastRow = sheet.getLastRow();
    let newIdNum = 1;
    if (lastRow > 1) {
      let ids = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      let maxId = 0;
      for(let i=0; i<ids.length; i++) {
        let currentStr = String(ids[i][0]).replace(/[^0-9]/g, '');
        let current = parseInt(currentStr);
        if(!isNaN(current) && current > maxId) maxId = current;
      }
      newIdNum = maxId + 1;
    }
    let newId = newIdNum.toString();
    
    sheet.appendRow([newId, name, address, "Regular", "Retail", geo, "", salesName, day]);
    
    // Also inject to today's route
    injectOutletToSales(salesName, day, name);
    invalidateAllCaches();
    return { success: true, message: "Outlet berhasil didaftarkan dengan ID: " + newId };
  } catch(e) { return { success: false, message: e.toString() }; }
}"""

    if old_handle in content:
        content = content.replace(old_handle, new_handle)
    if old_noo in content:
        content = content.replace(old_noo, new_noo)
        
    with open(file_name, "w") as f:
        f.write(content)
    print(f"Patched {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
