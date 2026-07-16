code = """
// =========================================================================
// WRAPPER FUNCTIONS FOR FRONTEND WEB APP
// =========================================================================

function attemptLogin(username, password) {
  try {
    const ms = new MasterService();
    const dataStr = ms.getUsers();
    const users = JSON.parse(dataStr);
    const userList = Array.isArray(users) ? users : (users.data || []);
    const user = userList.find(u => String(u.username).trim() === String(username).trim() && String(u.password).trim() === String(password).trim());
    
    if (user) {
      return { success: true, user: user };
    }
    return { success: false, message: "Username atau password salah." };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function fetchStaticMaster() {
  try {
    const ms = new MasterService();
    const uSheet = ms.getSheet("Users");
    const users = uSheet ? SheetUtil.getDataAsObjects(uSheet) : [];
    
    const pSheet = ms.getSheet("Products");
    const products = pSheet ? SheetUtil.getDataAsObjects(pSheet) : [];
    
    const sSheet = ms.getSheet("Stokis");
    const stokisMaster = sSheet ? SheetUtil.getDataAsObjects(sSheet) : [];
    
    return {
      success: true,
      users: users,
      products: products,
      stokisMaster: stokisMaster
    };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function fetchDynamicData() {
  try {
    const ms = new MasterService();
    
    // Check if InjectOutlets exists, else empty
    const ioSheet = ms.getSheet("InjectOutlets");
    const injectOutlets = ioSheet ? SheetUtil.getDataAsObjects(ioSheet) : [];
    
    // InjectStock maps to StockAllocations in the backend
    const sss = new BaseSpreadsheetService("STOCK_SALES");
    const isSheet = sss.getSheet("StockAllocations");
    const injectStock = isSheet ? SheetUtil.getDataAsObjects(isSheet) : [];
    
    const ssSheet = sss.getSheet("StokisStock");
    const stokisStock = ssSheet ? SheetUtil.getDataAsObjects(ssSheet) : [];
    
    const isv = new InvoiceService();
    const currentMonth = DateUtil.getYearMonth(new Date().toISOString());
    const rawDataSheet = isv.getSheet("TRX_" + currentMonth);
    const rawData = rawDataSheet ? SheetUtil.getDataAsObjects(rawDataSheet) : [];
    
    return {
      success: true,
      injectOutlets: injectOutlets,
      injectStock: injectStock,
      stokisStock: stokisStock,
      rawData: rawData
    };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function fetchInitialData() {
  // Frontend might call this?
  return fetchStaticMaster();
}

function saveUser(id, username, role, password) {
  try {
    const ms = new MasterService();
    const sheet = ms.getSheet("Users");
    if (!sheet) return { success: false, message: "Sheet Users tidak ditemukan" };
    
    let lastRow = sheet.getLastRow();
    let isUpdate = false;
    
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 4).getValues();
      for (let i = 0; i < data.length; i++) {
        if (data[i][0] === id) {
          sheet.getRange(i + 2, 2, 1, 3).setValues([[username, password, role]]);
          isUpdate = true;
          break;
        }
      }
    }
    
    if (!isUpdate) {
      sheet.appendRow([id, username, password, role]);
    }
    
    return { success: true, message: "User berhasil disimpan" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function deleteUser(id) {
  try {
    const ms = new MasterService();
    const sheet = ms.getSheet("Users");
    if (!sheet) return { success: false, message: "Sheet Users tidak ditemukan" };
    
    let lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      for (let i = 0; i < data.length; i++) {
        if (data[i][0] === id) {
          sheet.deleteRow(i + 2);
          return { success: true, message: "User berhasil dihapus" };
        }
      }
    }
    return { success: false, message: "User tidak ditemukan" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function saveProduct(id, name, retail, wholesale, stock) {
  try {
    const ms = new MasterService();
    const sheet = ms.getSheet("Products");
    if (!sheet) return { success: false, message: "Sheet Products tidak ditemukan" };
    
    let lastRow = sheet.getLastRow();
    let isUpdate = false;
    
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      for (let i = 0; i < data.length; i++) {
        if (data[i][0] === id) {
          sheet.getRange(i + 2, 2, 1, 4).setValues([[name, retail, wholesale, stock]]);
          isUpdate = true;
          break;
        }
      }
    }
    
    if (!isUpdate) {
      sheet.appendRow([id, name, retail, wholesale, stock]);
    }
    
    return { success: true, message: "Product berhasil disimpan" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function deleteProduct(id) {
  try {
    const ms = new MasterService();
    const sheet = ms.getSheet("Products");
    if (!sheet) return { success: false, message: "Sheet Products tidak ditemukan" };
    
    let lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      for (let i = 0; i < data.length; i++) {
        if (data[i][0] === id) {
          sheet.deleteRow(i + 2);
          return { success: true, message: "Product berhasil dihapus" };
        }
      }
    }
    return { success: false, message: "Product tidak ditemukan" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function saveMasterStokis(id, name, address, sales) {
  try {
    const ms = new MasterService();
    let sheet = ms.getSheet("Stokis");
    if (!sheet) sheet = ms.insertSheet("Stokis");
    
    // ensure headers
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["ID", "Name", "Address", "SalesAuthority"]);
    }
    
    let lastRow = sheet.getLastRow();
    let isUpdate = false;
    
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      for (let i = 0; i < data.length; i++) {
        if (data[i][0] === id) {
          sheet.getRange(i + 2, 2, 1, 3).setValues([[name, address, sales]]);
          isUpdate = true;
          break;
        }
      }
    }
    
    if (!isUpdate) {
      sheet.appendRow([id, name, address, sales]);
    }
    
    return { success: true, message: "Stokis berhasil disimpan" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function deleteMasterStokis(id) {
  try {
    const ms = new MasterService();
    const sheet = ms.getSheet("Stokis");
    if (!sheet) return { success: false, message: "Sheet Stokis tidak ditemukan" };
    
    let lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      for (let i = 0; i < data.length; i++) {
        if (data[i][0] === id) {
          sheet.deleteRow(i + 2);
          return { success: true, message: "Stokis berhasil dihapus" };
        }
      }
    }
    return { success: false, message: "Stokis tidak ditemukan" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function injectToStokis(targetId, payload) {
  try {
    const sss = new BaseSpreadsheetService("STOCK_SALES");
    let sheet = sss.getSheet("StokisStock");
    if (!sheet) sheet = sss.insertSheet("StokisStock");
    
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["StokisId", "ProductId", "Qty", "Date"]);
    }
    
    const dateStr = new Date().toISOString();
    
    // Prepare Master products lookup to get name? Wait, StokisStock schema in Android app expects:
    // productId, name, qty, stokisId, lastUpdated
    // Actually, let's just save productId, stokisId, qty, date.
    
    const ms = new MasterService();
    const pSheet = ms.getSheet("Products");
    let pMap = {};
    if (pSheet && pSheet.getLastRow() > 1) {
      let data = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, 2).getValues();
      data.forEach(r => pMap[r[0]] = r[1]);
    }

    let rows = [];
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
    
    return { success: true, message: "Stok berhasil di-inject ke Stokis" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function injectToSales(salesName, payload) {
  try {
    const sss = new BaseSpreadsheetService("STOCK_SALES");
    let sheet = sss.getSheet("StockAllocations");
    if (!sheet) sheet = sss.insertSheet("StockAllocations");
    
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["Sales", "ProductId", "ProductName", "Qty", "Date"]);
    }
    
    const dateStr = new Date().toISOString();
    
    const ms = new MasterService();
    const pSheet = ms.getSheet("Products");
    let pMap = {};
    if (pSheet && pSheet.getLastRow() > 1) {
      let data = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, 2).getValues();
      data.forEach(r => pMap[r[0]] = r[1]);
    }

    let rows = [];
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
    
    return { success: true, message: "Stok berhasil di-inject ke Sales" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function injectFromStokisToSales(stokisId, salesName, payload) {
  // Similar to injectToSales but maybe reduce Stokis stock?
  // For simplicity, just append to StockAllocations and append negative to StokisStock
  try {
    const sss = new BaseSpreadsheetService("STOCK_SALES");
    let stSheet = sss.getSheet("StokisStock");
    let saSheet = sss.getSheet("StockAllocations");
    
    if (!stSheet) stSheet = sss.insertSheet("StokisStock");
    if (!saSheet) saSheet = sss.insertSheet("StockAllocations");
    
    const dateStr = new Date().toISOString();
    
    const ms = new MasterService();
    const pSheet = ms.getSheet("Products");
    let pMap = {};
    if (pSheet && pSheet.getLastRow() > 1) {
      let data = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, 2).getValues();
      data.forEach(r => pMap[r[0]] = r[1]);
    }

    let saRows = [];
    let stRows = [];
    
    payload.forEach(item => {
      // Add to sales
      saRows.push([
         salesName,
         item.productId,
         pMap[item.productId] || item.productId,
         item.qty,
         dateStr
      ]);
      // Remove from stokis
      stRows.push([
         stokisId,
         item.productId,
         pMap[item.productId] || item.productId,
         -item.qty,
         dateStr
      ]);
    });
    
    if (saRows.length > 0) {
      if (saSheet.getLastRow() === 0) saSheet.appendRow(["Sales", "ProductId", "ProductName", "Qty", "Date"]);
      saSheet.getRange(saSheet.getLastRow() + 1, 1, saRows.length, 5).setValues(saRows);
    }
    
    if (stRows.length > 0) {
      if (stSheet.getLastRow() === 0) stSheet.appendRow(["StokisId", "ProductId", "ProductName", "Qty", "Date"]);
      stSheet.getRange(stSheet.getLastRow() + 1, 1, stRows.length, 5).setValues(stRows);
    }
    
    return { success: true, message: "Berhasil ditarik ke mobil Anda" };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function injectOutletToSales(salesName, day, outletName) {
  try {
    const ms = new MasterService();
    let sheet = ms.getSheet("InjectOutlets");
    if (!sheet) {
      sheet = ms.insertSheet("InjectOutlets");
      sheet.appendRow(["Sales", "Day", "Outlet", "Date"]);
    }
    
    sheet.appendRow([salesName, day, outletName, new Date().toISOString()]);
    return { success: true, message: "Outlet berhasil di-inject ke rute " + salesName };
  } catch(e) { return { success: false, message: e.toString() }; }
}

function saveOutletNOO(name, address, geo, day, salesName) {
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
}

function executeRealArchive() {
  try {
    ArchiveService.archiveOldTransactions(false);
    return { success: true, message: "Proses Archive berhasil diselesaikan." };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

// Ensure the new wrappers are at the end.
"""

with open("AppsScript_Full.gs", "a") as f:
    f.write(code)

with open("AppsScript.gs", "a") as f:
    f.write(code)

print("Wrappers generated")
