def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_sales = """    let rows = [];
    payload.forEach(item => {
      rows.push([
         salesName,
         item.productId,
         pMap[item.productId] || item.productId,
         item.qty,
         dateStr
      ]);
    });
    
    if (rows.length > 0) {
      sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, 5).setValues(rows);
    }
    
    return { success: true, message: "Stok berhasil di-inject ke Sales" };"""
    
    new_sales = """    let rows = [];
    payload.forEach(item => {
      rows.push([
         salesName,
         item.productId,
         pMap[item.productId] || item.productId,
         item.qty,
         dateStr
      ]);
      
      // Reduce Master Stock
      if (pSheet && pSheet.getLastRow() > 1) {
        let pData = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, 5).getValues();
        for (let i = 0; i < pData.length; i++) {
          if (pData[i][0] === item.productId) {
            let currentStock = parseInt(pData[i][4]) || 0;
            pSheet.getRange(i + 2, 5).setValue(currentStock - item.qty);
            break;
          }
        }
      }
    });
    
    if (rows.length > 0) {
      sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, 5).setValues(rows);
    }
    
    invalidateAllCaches();
    return { success: true, message: "Stok berhasil di-inject ke Sales" };"""

    old_stokis = """    let rows = [];
    payload.forEach(item => {
      rows.push([
         targetId,
         item.productId,
         pMap[item.productId] || item.productId, // name
         item.qty,
         dateStr
      ]);
    });
    
    // Instead of replacing, we should probably append. The backend Android logic groups by productId.
    if (rows.length > 0) {
      sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, 5).setValues(rows);
    }
    
    return { success: true, message: "Stok berhasil di-inject ke Stokis" };"""
    
    new_stokis = """    let rows = [];
    payload.forEach(item => {
      rows.push([
         targetId,
         item.productId,
         pMap[item.productId] || item.productId, // name
         item.qty,
         dateStr
      ]);
      
      // Reduce Master Stock
      if (pSheet && pSheet.getLastRow() > 1) {
        let pData = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, 5).getValues();
        for (let i = 0; i < pData.length; i++) {
          if (pData[i][0] === item.productId) {
            let currentStock = parseInt(pData[i][4]) || 0;
            pSheet.getRange(i + 2, 5).setValue(currentStock - item.qty);
            break;
          }
        }
      }
    });
    
    if (rows.length > 0) {
      sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, 5).setValues(rows);
    }
    
    invalidateAllCaches();
    return { success: true, message: "Stok berhasil di-inject ke Stokis" };"""

    if old_sales in content: content = content.replace(old_sales, new_sales)
    if old_stokis in content: content = content.replace(old_stokis, new_stokis)

    with open(file_name, "w") as f:
        f.write(content)
    print(f"Patched {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
