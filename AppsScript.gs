// =========================================================================
// SCRIPT GOOGLE APPS SCRIPT (APPSCRIPT.GS) UNTUK INTEGRASI SFA APP
// ARCHITECTURE: SERVICE REPOSITORY PATTERN DENGAN MONITORING
// =========================================================================

// =========================================================================
// CONFIGURATION (Default Fallbacks)
// =========================================================================
const CONFIG = {
  DB: {
    // New User Configuration
    USERS: '1-a74bo6GPqB9eGMQXuujJSpvjmvDVZC0rYMpmVm1aaw',
    PRODUCTS: '1-a74bo6GPqB9eGMQXuujJSpvjmvDVZC0rYMpmVm1aaw',
    RECEIPT_SETTINGS: '1-a74bo6GPqB9eGMQXuujJSpvjmvDVZC0rYMpmVm1aaw',
    OUTLETS: '1a_SFvMuA2g-nGiuxut7GufAHNyLhhH9St28w9H6rWjc',
    ROUTE_ASSIGNMENTS: '1X5Xvud5D02X3MhHmPZqAm6lf0E0A6mrIV5ypbfEPSPE',
    STOKIS: '1eLDylNJGDGEtBEWLM57CF3pXFTYSSPzegU2CgA33DMs',
    STOKIS_STOCK: '1eLDylNJGDGEtBEWLM57CF3pXFTYSSPzegU2CgA33DMs',
    STOCK_ALLOCATIONS: '1eLDylNJGDGEtBEWLM57CF3pXFTYSSPzegU2CgA33DMs',
    TRANSACTIONS: '1Ymm6qcO4pRAzksZ_a23Xyq1z0Pr1GE7SE4Zx0EGZgAs',
    PAYMENTS: '1UCE19QxfNT94Qdu5BwrrMbUwjLOFlLuEWPDdUeDjxas',
    REPORTS: '1cwfN4Z6WSiMpft8S8osaZfFmfL22O2_dAeZUBd8LZXg',
    SYNC_STATUS: '1Cv9Xz3SRAiANHfiJXPMwoCHq0cDXVb6krvYHrPFi2kA',
    SYNC_LOGS: '1BI4hu9Yh3vBvwzC5DZ_7hk9VW2ibLkCVLQWKeFtDZmw',

    // Backward compatibility mappings
    MASTER: '1-a74bo6GPqB9eGMQXuujJSpvjmvDVZC0rYMpmVm1aaw',
    STOCK_GUDANG: '1-a74bo6GPqB9eGMQXuujJSpvjmvDVZC0rYMpmVm1aaw',
    INVOICE: '1Ymm6qcO4pRAzksZ_a23Xyq1z0Pr1GE7SE4Zx0EGZgAs',
    STOCK_SALES: '1eLDylNJGDGEtBEWLM57CF3pXFTYSSPzegU2CgA33DMs',
    DASHBOARD: '1cwfN4Z6WSiMpft8S8osaZfFmfL22O2_dAeZUBd8LZXg',
    HISTORY: '1cwfN4Z6WSiMpft8S8osaZfFmfL22O2_dAeZUBd8LZXg',
    SYNC: '1Cv9Xz3SRAiANHfiJXPMwoCHq0cDXVb6krvYHrPFi2kA'
  },
  CACHE_EXPIRATION: 300,
  DEFAULT_ARCHIVE_KEEP_MONTHS: 3,
  DEFAULT_CHUNK_SIZE: 5000,
  DEFAULT_LOCK_TIMEOUT: 30000
};

// =========================================================================
// UTILS
// =========================================================================

// PERBAIKAN BUG KRITIS SINKRONISASI: Aplikasi Android (lihat SyncService.kt ->
// GzipRequestInterceptor) SELALU mengompres body setiap request POST dengan
// gzip dan mengirim header "Content-Encoding: gzip". Namun Apps Script TIDAK
// pernah melakukan dekompresi otomatis terhadap body request masuk -- kode
// lama langsung memanggil JSON.parse(e.postData.contents) pada body yang
// sebenarnya masih berupa data biner gzip. Akibatnya SETIAP transaksi/outlet
// baru/master data yang di-push dari HP (baik sync manual maupun SyncWorker
// otomatis) gagal di-parse di server -- ini yang menyebabkan data yang
// diinput di aplikasi Android tidak pernah benar-benar sampai/muncul di
// Google Sheets (dan karenanya juga tidak muncul lagi di HP lain / web).
// Fungsi ini mencoba men-dekompresi body sebagai gzip terlebih dahulu (sesuai
// perilaku asli client Android), dan HANYA jika itu gagal (mis. request dari
// curl/Postman/versi app lama yang belum mengompres), baru fallback membaca
// body apa adanya sebagai teks biasa. Dengan begitu kompatibel untuk kedua
// jenis pengirim.
function decodePostBody(e) {
  if (!e || !e.postData) return "";
  try {
    const bytes = e.postData.getBytes();
    const gzipBlob = Utilities.newBlob(bytes, "application/x-gzip");
    const decompressed = Utilities.ungzip(gzipBlob);
    return decompressed.getDataAsString("UTF-8");
  } catch (gzipErr) {
    // Bukan data gzip (atau gagal didekompresi) -> anggap body adalah teks/JSON biasa.
    return e.postData.contents;
  }
}

function executeWithLock(func) {
  const lock = LockService.getScriptLock();
  const timeout = CONFIG.DEFAULT_LOCK_TIMEOUT;
  if (lock.tryLock(timeout)) {
    try {
      return func();
    } finally {
      lock.releaseLock();
    }
  } else {
    throw new Error("Sistem sedang sibuk memproses permintaan lain. Silakan coba beberapa saat lagi.");
  }
}

class CacheUtil {
  static get(key) {
    const val = CacheService.getScriptCache().get(key);
    this.recordHitMiss(val !== null);
    return val;
  }
  
  static put(key, value, exp = CONFIG.CACHE_EXPIRATION) {
    if (value && value.length < 100000) {
      CacheService.getScriptCache().put(key, value, exp);
    }
  }

  static getWithLock(key, computeFunc, exp = CONFIG.CACHE_EXPIRATION) {
    let cached = this.get(key);
    if (cached) return cached;

    const lock = LockService.getScriptLock();
    // Wait up to 5 seconds for the lock to prevent cache stampede
    if (lock.tryLock(5000)) {
      try {
        // Double-check cache after acquiring lock
        cached = CacheService.getScriptCache().get(key);
        if (cached) return cached;
        
        const result = computeFunc();
        this.put(key, result, exp);
        return result;
      } finally {
        lock.releaseLock();
      }
    } else {
      // Fallback: compute without caching if lock timeout
      return computeFunc();
    }
  }

  static recordHitMiss(isHit) {
    try {
      const props = PropertiesService.getScriptProperties();
      // Sample 10% to save quota
      if (Math.random() < 0.1) {
        let stats = props.getProperty("CACHE_STATS");
        stats = stats ? JSON.parse(stats) : { hit: 0, miss: 0 };
        if (isHit) stats.hit += 10; else stats.miss += 10;
        props.setProperty("CACHE_STATS", JSON.stringify(stats));
      }
    } catch(e) {}
  }

  static getStats() {
    try {
      const props = PropertiesService.getScriptProperties();
      let stats = props.getProperty("CACHE_STATS");
      return stats ? JSON.parse(stats) : { hit: 0, miss: 0 };
    } catch(e) { return { hit: 0, miss: 0 }; }
  }
}

class ResponseUtil {
  static success(data) {
    return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON);
  }
  static error(message) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message})).setMimeType(ContentService.MimeType.JSON);
  }
}

class DateUtil {
  static getYearMonth(dateStr) {
    if (dateStr && dateStr.length >= 7 && dateStr.charAt(4) === '-') {
      return dateStr.substring(0, 7).replace("-", "_");
    }

    // PERBAIKAN BUG KRITIS: dateStr yang dikirim aplikasi Android berformat
    // "dd/MM/yyyy HH:mm" (mis. "13/07/2026 14:30"). new Date(dateStr) versi
    // JavaScript SELALU menafsirkan format bergaris-miring sebagai MM/DD/YYYY
    // (gaya Amerika), TANPA peduli locale spreadsheet. Akibatnya:
    //  - Untuk tanggal > 12 (mayoritas hari dalam sebulan, mis. tanggal 13-31):
    //    "bulan" hasil parse jadi > 12 -> Invalid Date -> yyyy/mm menjadi NaN
    //    -> transaksi tersimpan ke sheet rusak bernama "Transactions_NaN_aN"
    //    alih-alih sheet bulan yang benar.
    //  - Untuk tanggal <= 12 (mis. "01/07/2026" = 1 Juli): tanggal & bulan
    //    TERTUKAR secara diam-diam (dibaca sebagai 7 Januari) -> transaksi
    //    tersimpan ke sheet BULAN YANG SALAH tanpa error apapun.
    // Ini salah satu penyebab utama transaksi "hilang"/tidak ketemu di tab
    // bulan yang seharusnya. Diperbaiki dengan parsing manual via regex
    // dd/MM/yyyy, tidak lagi bergantung pada new Date(string) untuk format ini.
    const slashMatch = dateStr ? String(dateStr).match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})/) : null;
    if (slashMatch) {
      const mmRaw = parseInt(slashMatch[2], 10);
      const yyyy = slashMatch[3];
      const mm = ("0" + mmRaw).slice(-2);
      return `${yyyy}_${mm}`;
    }

    const d = dateStr ? new Date(dateStr) : new Date();
    const yyyy = d.getFullYear();
    const mm = ("0" + (d.getMonth() + 1)).slice(-2);
    return `${yyyy}_${mm}`;
  }

  static getTargetMonths(startStr, endStr) {
    let start = startStr ? new Date(startStr) : new Date();
    let end = endStr ? new Date(endStr) : new Date();
    if (isNaN(start.getTime())) start = new Date();
    if (isNaN(end.getTime())) end = new Date();
    
    let current = new Date(start.getFullYear(), start.getMonth(), 1);
    let endDate = new Date(end.getFullYear(), end.getMonth(), 1);
    
    let months = [];
    while (current <= endDate) {
      const yyyy = current.getFullYear();
      const mm = ("0" + (current.getMonth() + 1)).slice(-2);
      months.push(`${yyyy}_${mm}`);
      current.setMonth(current.getMonth() + 1);
    }
    return months;
  }
}

class SheetUtil {
  static getDataAsObjects(sheet, includeDeleted = false, lastSyncObj = null) {
    const lastRow = sheet.getLastRow();
    const lastCol = sheet.getLastColumn();
    if (lastRow <= 1 || lastCol === 0) return [];
    
    const data = sheet.getRange(1, 1, lastRow, lastCol).getValues();
    const headers = data[0];
    const result = [];
    
    const isActiveIdx = headers.indexOf("IsActive");
    const statusIdx = headers.indexOf("Status");
    const updatedAtIdx = headers.indexOf("UpdatedAt");
    
    for (let i = 1; i < data.length; i++) {
      const row = data[i];
      if (!includeDeleted) {
        if (isActiveIdx !== -1 && (row[isActiveIdx] === false || row[isActiveIdx] === "FALSE" || row[isActiveIdx] === 0)) continue;
        if (statusIdx !== -1 && (row[statusIdx] === "DELETED" || row[statusIdx] === "INACTIVE")) continue;
      }
      
      if (lastSyncObj && updatedAtIdx !== -1 && row[updatedAtIdx]) {
        let rowUpdatedAt = new Date(row[updatedAtIdx]);
        if (!isNaN(rowUpdatedAt.getTime()) && rowUpdatedAt <= lastSyncObj) {
           continue; 
        }
      }
      
      const obj = {};
      for (let j = 0; j < headers.length; j++) {
                let key = headers[j] ? String(headers[j]).trim() : "Col" + j;
        let cleanKey = key.replace(/\s+/g, '').toLowerCase();
        obj[key] = row[j];
        // add case variants to make it bulletproof
        if (cleanKey === 'stokisid') { obj['StokisID'] = row[j]; obj['stokisId'] = row[j]; }
        if (cleanKey === 'productid') { obj['ProductID'] = row[j]; obj['productId'] = row[j]; obj['Product'] = row[j]; obj['product'] = row[j]; }
        if (cleanKey === 'productname' || cleanKey === 'product') { obj['Product'] = row[j]; obj['product'] = row[j]; obj['ProductName'] = row[j]; }
        if (cleanKey === 'qty') { obj['Qty'] = row[j]; obj['qty'] = row[j]; }
        if (cleanKey === 'date') { obj['Date'] = row[j]; obj['date'] = row[j]; }
        if (cleanKey === 'sales' || cleanKey === 'salesauthority') { obj['Sales'] = row[j]; obj['sales'] = row[j]; obj['SalesAuthority'] = row[j]; obj['salesAuthority'] = row[j]; obj['SalesName'] = row[j]; obj['salesName'] = row[j]; }
        if (cleanKey === 'username' || cleanKey === 'name') { obj['Username'] = row[j]; obj['username'] = row[j]; obj['Name'] = row[j]; obj['name'] = row[j]; }
        if (cleanKey === 'password') { obj['Password'] = row[j]; obj['password'] = row[j]; }
        if (cleanKey === 'role') { obj['Role'] = row[j]; obj['role'] = row[j]; }
        if (cleanKey === 'id') { obj['ID'] = row[j]; obj['id'] = row[j]; }
        if (cleanKey === 'address') { obj['Address'] = row[j]; obj['address'] = row[j]; }
        if (cleanKey === 'type') { obj['Type'] = row[j]; obj['type'] = row[j]; }
        if (cleanKey === 'geotag' || cleanKey === 'geotaglocation') { obj['Geotag'] = row[j]; obj['geotag'] = row[j]; }
        if (cleanKey === 'retail' || cleanKey === 'priceretail') { obj['Retail'] = row[j]; obj['retail'] = row[j]; obj['PriceRetail'] = row[j]; obj['priceRetail'] = row[j]; }
        if (cleanKey === 'wholesale' || cleanKey === 'pricewholesale') { obj['Wholesale'] = row[j]; obj['wholesale'] = row[j]; obj['PriceWholesale'] = row[j]; obj['priceWholesale'] = row[j]; }
        if (cleanKey === 'stock' || cleanKey === 'warehousestock') { obj['Stock'] = row[j]; obj['stock'] = row[j]; }
      }
      result.push(obj);
    }
    return result;
  }
}

// =========================================================================
// SERVICES LAYER
// =========================================================================


const SPREADSHEET_CACHE = {};

class BaseSpreadsheetService {
  constructor(dbKey) {
    this.dbKey = dbKey;
    this.ss = null;
  }
  
  getSpreadsheet() {
    if (!this.ss) {
      let key = this.dbKey;
      // Map old dbKey names to new dbKey names for safety
      if (key === "MASTER") key = "USERS";
      else if (key === "STOCK_GUDANG") key = "PRODUCTS";
      else if (key === "INVOICE") key = "TRANSACTIONS";
      else if (key === "STOCK_SALES") key = "STOKIS_STOCK";
      else if (key === "DASHBOARD") key = "REPORTS";
      else if (key === "HISTORY") key = "REPORTS";
      else if (key === "SYNC") key = "SYNC_LOGS";

      if (!SPREADSHEET_CACHE[key]) {
        let id = CONFIG.DB[key] || CONFIG.DB[this.dbKey];
        let ss = null;
        try {
          if (id) ss = SpreadsheetApp.openById(id);
        } catch (e) {
          // Fallback to CONFIG.DB.USERS
          try {
            if (CONFIG.DB.USERS) ss = SpreadsheetApp.openById(CONFIG.DB.USERS);
          } catch (err) {
            // Fallback to Active Spreadsheet
            try {
              ss = SpreadsheetApp.getActiveSpreadsheet();
            } catch (err2) {
              ss = null;
            }
          }
        }
        if (!ss) {
          try {
            ss = SpreadsheetApp.getActiveSpreadsheet();
          } catch (err) {
            ss = null;
          }
        }
        SPREADSHEET_CACHE[key] = ss;
      }
      this.ss = SPREADSHEET_CACHE[key];
    }
    return this.ss;
  }

  getSpreadsheetForSheet(sheetName) {
    let key = this.dbKey; // fallback
    const name = (sheetName || "").trim().toLowerCase();
    
    if (name === "users") {
      key = "USERS";
    } else if (name === "products") {
      key = "PRODUCTS";
    } else if (name === "setting") {
      key = "RECEIPT_SETTINGS";
    } else if (name === "outlets" || name === "injectoutlets") {
      key = "OUTLETS";
    } else if (name === "routeassignments" || name === "route_assignments") {
      key = "ROUTE_ASSIGNMENTS";
    } else if (name === "stokis") {
      key = "STOKIS";
    } else if (name === "stokisstock" || name === "stokis_stock") {
      key = "STOKIS_STOCK";
    } else if (name === "stockallocations" || name === "stock_allocations") {
      key = "STOCK_ALLOCATIONS";
    } else if (name.startsWith("transactions") || name.startsWith("trx_")) {
      key = "TRANSACTIONS";
    } else if (name === "payments") {
      key = "PAYMENTS";
    } else if (name === "salessummary" || name === "productsummary" || name === "reports") {
      key = "REPORTS";
    } else if (name === "syncstatus" || name === "sync_status") {
      key = "SYNC_STATUS";
    } else if (name.endsWith("log") || name === "systemmetrics" || name === "sync_logs") {
      key = "SYNC_LOGS";
    }

    if (!SPREADSHEET_CACHE[key]) {
      let id = CONFIG.DB[key] || CONFIG.DB[this.dbKey];
      let ss = null;
      try {
        if (id) ss = SpreadsheetApp.openById(id);
      } catch (e) {
        // Fallback to CONFIG.DB.USERS
        try {
          if (CONFIG.DB.USERS) ss = SpreadsheetApp.openById(CONFIG.DB.USERS);
        } catch (err) {
          // Fallback to Active Spreadsheet
          try {
            ss = SpreadsheetApp.getActiveSpreadsheet();
          } catch (err2) {
            ss = null;
          }
        }
      }
      if (!ss) {
        try {
          ss = SpreadsheetApp.getActiveSpreadsheet();
        } catch (err) {
          ss = null;
        }
      }
      SPREADSHEET_CACHE[key] = ss;
    }
    return SPREADSHEET_CACHE[key];
  }

  getSheet(sheetName) {
    return this.getSpreadsheetForSheet(sheetName).getSheetByName(sheetName);
  }
  
  insertSheet(sheetName) {
    return this.getSpreadsheetForSheet(sheetName).insertSheet(sheetName);
  }
  
  getValues(sheetName) {
    const sheet = this.getSheet(sheetName);
    return sheet ? SheetUtil.getDataAsObjects(sheet) : [];
  }
  
  getCachedValues(sheetName) {
    const version = PropertiesService.getScriptProperties().getProperty("MASTER_DATA_VERSION") || "v1";
    const cacheKey = "getCachedValues_" + sheetName + "_" + version;
    const jsonString = CacheUtil.getWithLock(cacheKey, () => {
      const data = this.getValues(sheetName);
      return JSON.stringify(data);
    }, CONFIG.CACHE_EXPIRATION);
    return JSON.parse(jsonString);
  }
}

class ConfigService extends BaseSpreadsheetService {
  constructor() { super("MASTER"); }
  
  getAppConfig() {
    const CACHE_KEY = "AppConfigSettings";
    let cached = CacheUtil.get(CACHE_KEY);
    if (cached) return JSON.parse(cached);
    
    let config = {
      archiveKeepMonths: CONFIG.DEFAULT_ARCHIVE_KEEP_MONTHS,
      chunkSize: CONFIG.DEFAULT_CHUNK_SIZE,
      lockTimeout: CONFIG.DEFAULT_LOCK_TIMEOUT
    };
    
    try {
      const settings = this.getValues("Setting"); 
      if (settings && settings.length > 0) {
        for (let row of settings) {
          if (row.Key === "ARCHIVE_KEEP_MONTHS") config.archiveKeepMonths = parseInt(row.Value) || config.archiveKeepMonths;
          if (row.Key === "CHUNK_SIZE") config.chunkSize = parseInt(row.Value) || config.chunkSize;
          if (row.Key === "LOCK_TIMEOUT") config.lockTimeout = parseInt(row.Value) || config.lockTimeout;
        }
      }
    } catch(e) {}
    
    CacheUtil.put(CACHE_KEY, JSON.stringify(config));
    return config;
  }
}

class LogService extends BaseSpreadsheetService {
  constructor() { super("SYNC"); }
  
  logDevice(deviceId, appVersion, salesId, action) {
    try {
      let sheet = this.getSheet("DeviceLog") || this.insertSheet("DeviceLog");
      if (sheet.getLastRow() === 0) sheet.appendRow(["Timestamp", "DeviceId", "AppVersion", "SalesId", "Action"]);
      sheet.appendRow([new Date(), deviceId, appVersion, salesId, action]);
    } catch(e) {}
  }
  
  logError(error, context) {
    try {
      let sheet = this.getSheet("ErrorLog") || this.insertSheet("ErrorLog");
      if (sheet.getLastRow() === 0) sheet.appendRow(["Timestamp", "Context", "ErrorMessage", "Stack"]);
      sheet.appendRow([new Date(), context, error.message, error.stack]);
    } catch(e) {}
  }
  
  logAction(actionName, description) {
    try {
      let sheet = this.getSheet("SyncLog") || this.insertSheet("SyncLog");
      if (sheet.getLastRow() === 0) sheet.appendRow(["Timestamp", "Action", "Description"]);
      sheet.appendRow([new Date(), actionName, description]);
    } catch(e) {}
  }

  logArchive(sheetName, status, rowCount, message) {
    try {
      let sheet = this.getSheet("ArchiveLog") || this.insertSheet("ArchiveLog");
      if (sheet.getLastRow() === 0) sheet.appendRow(["Timestamp", "SheetName", "Status", "RowCount", "Message"]);
      sheet.appendRow([new Date(), sheetName, status, rowCount, message]);
    } catch(e) {}
  }

  logPerformance(action, durationMs, details) {
    try {
      let sheet = this.getSheet("PerformanceLog") || this.insertSheet("PerformanceLog");
      if (sheet.getLastRow() === 0) sheet.appendRow(["Timestamp", "Action", "DurationMs", "Details"]);
      sheet.appendRow([new Date(), action, durationMs, details]);
    } catch(e) {}
  }
  
  getLastArchiveLog() {
    try {
      let sheet = this.getSheet("ArchiveLog");
      if (!sheet) return null;
      let lastRow = sheet.getLastRow();
      if (lastRow <= 1) return null;
      let data = sheet.getRange(lastRow, 1, 1, 5).getValues()[0];
      return {
        timestamp: data[0],
        sheetName: data[1],
        status: data[2],
        rowCount: data[3],
        message: data[4]
      };
    } catch(e) { return null; }
  }

  getArchiveHistory() {
    try {
      let sheet = this.getSheet("ArchiveLog");
      if (!sheet) return [];
      
      const lastRow = sheet.getLastRow();
      if (lastRow <= 1) return [];
      
      const startRow = Math.max(2, lastRow - 100 + 1);
      const numRows = lastRow - startRow + 1;
      
      const data = sheet.getRange(startRow, 1, numRows, 5).getValues();
      const result = [];
      for (let i = data.length - 1; i >= 0; i--) { 
        result.push({
          timestamp: data[i][0],
          sheetName: data[i][1],
          status: data[i][2],
          rowCount: data[i][3],
          message: data[i][4]
        });
      }
      return result;
    } catch(e) {
      return [];
    }
  }

  getPerformanceLogs() {
    try {
      let sheet = this.getSheet("PerformanceLog");
      if (!sheet) return [];
      
      const lastRow = sheet.getLastRow();
      if (lastRow <= 1) return [];
      
      const startRow = Math.max(2, lastRow - 50 + 1);
      const numRows = lastRow - startRow + 1;
      
      const data = sheet.getRange(startRow, 1, numRows, 4).getValues();
      const result = [];
      for (let i = data.length - 1; i >= 0; i--) {
        result.push({
          timestamp: data[i][0],
          action: data[i][1],
          durationMs: data[i][2],
          details: data[i][3]
        });
      }
      return result;
    } catch(e) {
      return [];
    }
  }

  getSystemMetrics() {
    try {
      let sheet = this.getSheet("SystemMetrics");
      if (!sheet) return {};
      let lastRow = sheet.getLastRow();
      if (lastRow <= 1) return {};
      const data = sheet.getRange(2, 1, Math.min(20, lastRow - 1), 2).getValues();
      let metrics = {};
      for (let row of data) {
        if (row[0]) metrics[row[0]] = row[1];
      }
      return metrics;
    } catch(e) { return {}; }
  }

  updateSystemMetrics(key, value) {
    try {
      let sheet = this.getSheet("SystemMetrics") || this.insertSheet("SystemMetrics");
      let lastRow = sheet.getLastRow();
      if (lastRow === 0) {
        sheet.appendRow(["Key", "Value"]);
        lastRow = 1;
      }
      
      let data = lastRow > 1 ? sheet.getRange(2, 1, lastRow - 1, 2).getValues() : [];
      let found = false;
      for (let i = 0; i < data.length; i++) {
        if (data[i][0] === key) {
          sheet.getRange(i + 2, 2).setValue(value);
          found = true;
          break;
        }
      }
      if (!found) {
        sheet.appendRow([key, value]);
      }
    } catch(e) {}
  }
}

const Logger = new LogService();
const AppConfig = new ConfigService();

class MasterService extends BaseSpreadsheetService {
  constructor() { super("MASTER"); }
  
  getCachedData(sheetName, cacheKey) {
    // Utilize lock to prevent cache stampede
    let jsonString = CacheUtil.getWithLock(cacheKey, () => {
      let data = this.getValues(sheetName);
      return JSON.stringify(data);
    }, CONFIG.CACHE_EXPIRATION);
    
    return jsonString;
  }
  

  getInitialData(lastSyncStr, clientVersion) {
    const version = PropertiesService.getScriptProperties().getProperty("MASTER_DATA_VERSION") || "v1";
    if (clientVersion && clientVersion === version) {
        return JSON.stringify({ version: version }); // version match, no need to send data
    }
    const cacheKey = "getInitialData_" + version + "_" + (lastSyncStr ? lastSyncStr.substring(0,10) : "all");
    let jsonString = CacheUtil.getWithLock(cacheKey, () => {
       const lastSyncObj = lastSyncStr ? new Date(lastSyncStr) : null;
       
       const salesStockService = new BaseSpreadsheetService("STOCK_SALES");
       
       let printerSettings = null;
       try {
         const settingsRows = this.getValues("Setting");
         if (settingsRows && settingsRows.length > 0) {
           printerSettings = {};
           for (let row of settingsRows) {
             if (row.Key === "PRINTER_HEADER") printerSettings.header = row.Value;
             if (row.Key === "PRINTER_FOOTER") printerSettings.footer = row.Value;
             if (row.Key === "PRINTER_HEADER_ALIGN") printerSettings.headerAlign = row.Value;
             if (row.Key === "PRINTER_FOOTER_ALIGN") printerSettings.footerAlign = row.Value;
             if (row.Key === "PRINTER_LOGO_BASE64") printerSettings.logoBase64 = row.Value;
             if (row.Key === "PRINTER_LOGO_ALIGN") printerSettings.logoAlign = row.Value;
           }
         }
       } catch(e) {}
       
       return JSON.stringify({
           users: this.getSheet("Users") ? SheetUtil.getDataAsObjects(this.getSheet("Users"), false, lastSyncObj) : [],
           products: this.getSheet("Products") ? SheetUtil.getDataAsObjects(this.getSheet("Products"), false, lastSyncObj) : [],
           outlets: this.getSheet("Outlets") ? SheetUtil.getDataAsObjects(this.getSheet("Outlets"), false, lastSyncObj) : [],
           
           stokis: this.getSheet("Stokis") ? SheetUtil.getDataAsObjects(this.getSheet("Stokis"), false, lastSyncObj) : [],
           stokisStock: salesStockService.getSheet("StokisStock") ? SheetUtil.getDataAsObjects(salesStockService.getSheet("StokisStock"), false, lastSyncObj) : [],
           stockAllocations: salesStockService.getSheet("StockAllocations") ? SheetUtil.getDataAsObjects(salesStockService.getSheet("StockAllocations"), false, lastSyncObj) : [],
           
           version: version,
           printerSettings: printerSettings
       });
    }, CONFIG.CACHE_EXPIRATION);
    return jsonString;
  }
  getUsers() { return this.getCachedData("Users", "getUsers"); }
  getProducts() { return this.getCachedData("Products", "getProducts"); }
  getOutlets() { return this.getCachedData("Outlets", "getOutlets"); }
}

class InvoiceService extends BaseSpreadsheetService {
  constructor() { super("INVOICE"); }
  
  getTransactions(salesId, startDate, endDate, lastSync) {
    const filterStartDate = startDate || lastSync || new Date(new Date().setMonth(new Date().getMonth() - 1)).toISOString();
    const targetMonths = DateUtil.getTargetMonths(filterStartDate, endDate);
    let filteredTransactions = [];
    
    const config = AppConfig.getAppConfig();
    
    for (let i = targetMonths.length - 1; i >= 0; i--) {
      const sheet = this.getSheet("Transactions_" + targetMonths[i]);
      if (sheet) {
        const monthlyTrx = this._getFilteredReverseChunked(sheet, salesId, startDate, endDate, lastSync, config.chunkSize);
        filteredTransactions = monthlyTrx.concat(filteredTransactions);
      }
    }
    return filteredTransactions;
  }
  
  bulkSaveTransactions(transactions) {
    let transactionsByMonth = {};
    for (let trx of transactions) {
      let ym = DateUtil.getYearMonth(trx.date);
      let sheetName = "Transactions_" + ym;
      if (!transactionsByMonth[sheetName]) transactionsByMonth[sheetName] = [];
      transactionsByMonth[sheetName].push(trx);
    }
    
    let insertedCount = 0;
    let skippedCount = 0;
    let validTransactions = [];
    const timestamp = new Date().toISOString();
    
    for (let sheetName in transactionsByMonth) {
      let sheet = this.getSheet(sheetName);
      if (!sheet) {
        sheet = this.insertSheet(sheetName);
        sheet.appendRow(["OrderId", "Date", "SalesId", "SalesName", "OutletId", "OutletName", "OutletType", "Geotag", "Total", "PaymentMethod", "Items", "UploadedAt"]);
      }
      
      let trxs = transactionsByMonth[sheetName];
      let lastRow = sheet.getLastRow();
      let existingOrderIds = {};
      
      if (lastRow > 1) {
        let headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
        let orderIdIdx = headers.indexOf("OrderId");
        if (orderIdIdx !== -1) {
          let orderIds = sheet.getRange(2, orderIdIdx + 1, lastRow - 1, 1).getValues();
          orderIds.forEach(row => { if (row[0]) existingOrderIds[row[0]] = true; });
        }
      }
      
      let rowsToInsert = [];
      for (let t of trxs) {
        if (!existingOrderIds[t.orderId]) {
          rowsToInsert.push([
            t.orderId, t.date, t.salesId, t.salesName, t.outletId || "", t.outletName || "",
            t.outletType || "", t.geotag || "", t.total, t.paymentMethod, typeof t.items === 'string' ? t.items : JSON.stringify(t.items), timestamp
          ]);
          existingOrderIds[t.orderId] = true;
          insertedCount++;
          validTransactions.push(t);
        } else {
          skippedCount++;
        }
      }
      
      if (rowsToInsert.length > 0) {
        sheet.getRange(lastRow + 1, 1, rowsToInsert.length, rowsToInsert[0].length).setValues(rowsToInsert);
      }
    }
    
    // PERBAIKAN PERFORMA (penting untuk skala banyak sales bersamaan): sebelumnya
    // counter "TRX_TODAY_" di-update lewat PropertiesService.setProperty() SATU
    // KALI PER TRANSAKSI di dalam loop. PropertiesService punya overhead network
    // per panggilan, jadi kalau satu sales upload 15 transaksi sekaligus, itu 15x
    // panggilan PropertiesService BERURUTAN selagi masih memegang lock global
    // (executeWithLock) -- memperlama waktu tunggu sales lain yang antre sync di
    // saat bersamaan (mis. auto-upload jam 12 siang untuk banyak sales). Sekarang
    // di-update SATU KALI SAJA per panggilan bulkSaveTransactions (bukan per baris),
    // hasil akhirnya sama persis, tapi jauh lebih cepat dan tidak menahan lock lama.
    if (insertedCount > 0) {
      try {
        const todayDateStr = new Date().toISOString().substring(0,10);
        const props = PropertiesService.getScriptProperties();
        const todayKey = "TRX_TODAY_" + todayDateStr;
        const todayCount = parseInt(props.getProperty(todayKey) || "0");
        props.setProperty(todayKey, (todayCount + insertedCount).toString());
      } catch(e) {}
    }
    
    return { insertedCount, skippedCount, validTransactions };
  }
  
  _getFilteredReverseChunked(sheet, salesId, startDate, endDate, lastSync, chunkSize) {
    const lastRow = sheet.getLastRow();
    const lastCol = sheet.getLastColumn();
    if (lastRow <= 1 || lastCol === 0) return [];

    const headers = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
    const dateIdx = headers.indexOf("Date");
    const uploadedAtIdx = headers.indexOf("UploadedAt");
    const salesIdIdx = headers.indexOf("SalesId");

    const startObj = startDate ? new Date(startDate) : null;
    const endObj = endDate ? new Date(endDate) : null;
    const lastSyncObj = lastSync ? new Date(lastSync) : null;

    const result = [];
    
    for (let endRow = lastRow; endRow > 1; endRow -= chunkSize) {
      let startRow = Math.max(2, endRow - chunkSize + 1);
      let numRows = endRow - startRow + 1;
      let data = sheet.getRange(startRow, 1, numRows, lastCol).getValues();
      let stopReading = false;
      
      for (let i = data.length - 1; i >= 0; i--) {
        let row = data[i];
        let match = true;
        
        if (lastSyncObj && uploadedAtIdx !== -1 && row[uploadedAtIdx]) {
          let rowUploadedAt = new Date(row[uploadedAtIdx]);
          if (!isNaN(rowUploadedAt.getTime()) && rowUploadedAt <= lastSyncObj) {
            stopReading = true;
            break;
          }
        }
        
        if (salesId && salesIdIdx !== -1 && row[salesIdIdx] != salesId) match = false;
        
        if (match && dateIdx !== -1 && (startObj || endObj)) {
          let rowDate = new Date(row[dateIdx]);
          if (startObj && rowDate < startObj) match = false;
          if (endObj && rowDate > endObj) match = false;
        }
        
        if (match) {
          let obj = {};
          for (let j = 0; j < headers.length; j++) {
            obj[headers[j]] = row[j];
          }
          result.unshift(obj);
        }
      }
      if (stopReading) break;
    }
    return result;
  }
}

class SummaryService extends BaseSpreadsheetService {
  constructor() { super("DASHBOARD"); }
  
  updateDashboard(transactions) {
    if (!transactions || transactions.length === 0) return;
    
    let salesSummary = this.getSheet("SalesSummary") || this.insertSheet("SalesSummary");
    if (salesSummary.getLastRow() === 0) salesSummary.appendRow(["SalesId", "SalesName", "TotalOmzet", "TotalTrx"]);
    
    let productSummary = this.getSheet("ProductSummary") || this.insertSheet("ProductSummary");
    if (productSummary.getLastRow() === 0) productSummary.appendRow(["ProductId", "ProductName", "TotalQty", "TotalOmzet"]);
    
    let salesAgg = {};
    let productAgg = {};
    
    for (let t of transactions) {
      if (!salesAgg[t.salesId]) salesAgg[t.salesId] = { name: t.salesName, omzet: 0, trx: 0 };
      salesAgg[t.salesId].omzet += (parseFloat(t.total) || 0);
      salesAgg[t.salesId].trx += 1;
      
      let items = [];
      try { items = typeof t.items === 'string' ? JSON.parse(t.items) : t.items; } catch(e){}
      
      for (let item of items) {
        if (!productAgg[item.productId]) productAgg[item.productId] = { name: item.productName, qty: 0, omzet: 0 };
        productAgg[item.productId].qty += (parseInt(item.quantity) || 0);
        productAgg[item.productId].omzet += (parseFloat(item.totalPrice) || 0);
      }
    }
    
    this._upsertSummary(salesSummary, salesAgg);
    this._upsertSummary(productSummary, productAgg);
  }
  
  _upsertSummary(sheet, agg) {
    let lastRow = sheet.getLastRow();
    let rowsToAppend = [];
    
    if (lastRow > 1) {
      let data = sheet.getRange(2, 1, lastRow - 1, 4).getValues();
      let mapRow = {};
      for (let i = 0; i < data.length; i++) mapRow[data[i][0]] = i;
      
      for (let id in agg) {
        if (mapRow[id] !== undefined) {
          let idx = mapRow[id];
          data[idx][2] = (parseFloat(data[idx][2]) || 0) + (agg[id].omzet !== undefined ? agg[id].omzet : agg[id].qty);
          data[idx][3] = (parseFloat(data[idx][3]) || 0) + (agg[id].trx !== undefined ? agg[id].trx : agg[id].omzet);
        } else {
          rowsToAppend.push([id, agg[id].name, agg[id].omzet !== undefined ? agg[id].omzet : agg[id].qty, agg[id].trx !== undefined ? agg[id].trx : agg[id].omzet]);
        }
      }
      sheet.getRange(2, 1, lastRow - 1, 4).setValues(data);
    } else {
      for (let id in agg) {
        rowsToAppend.push([id, agg[id].name, agg[id].omzet !== undefined ? agg[id].omzet : agg[id].qty, agg[id].trx !== undefined ? agg[id].trx : agg[id].omzet]);
      }
    }
    if (rowsToAppend.length > 0) {
      sheet.getRange(lastRow + 1, 1, rowsToAppend.length, 4).setValues(rowsToAppend);
    }
  }
}

class WarehouseService extends BaseSpreadsheetService {
  constructor() { super("STOCK_GUDANG"); }
  // Placeholder for future methods
}

class SalesStockService extends BaseSpreadsheetService {
  constructor() { super("STOCK_SALES"); }
  // Placeholder for future methods
}

class ArchiveService {
  static ensureSheetExists(ss, sheetName, sourceSheet, colCount) {
    let sheet = ss.getSheetByName(sheetName);
    if (!sheet) {
      sheet = ss.insertSheet(sheetName);
      if (colCount > 0) {
        const headers = sourceSheet.getRange(1, 1, 1, colCount).getValues();
        sheet.getRange(1, 1, 1, colCount).setValues(headers);
      }
    }
    return sheet;
  }

  static verifyArchive(srcSheet, destSheet, srcRowCount, srcColCount) {
    const destRowCount = destSheet.getLastRow();
    const destColCount = destSheet.getLastColumn();
    
    // 1. Check dimensions
    if (destRowCount < srcRowCount || destColCount < srcColCount) {
      return false;
    }
    
    if (srcRowCount <= 1) return true;
    
    // 2. Check headers
    const srcHeader = srcSheet.getRange(1, 1, 1, srcColCount).getValues()[0];
    const destHeader = destSheet.getRange(1, 1, 1, srcColCount).getValues()[0];
    if (JSON.stringify(srcHeader) !== JSON.stringify(destHeader)) return false;
    
    // 3. Aggregate Check (Total Omzet & Total Qty)
    const calculateAggregates = (sheet, startRow, numRows, colCount, headers) => {
       const data = sheet.getRange(startRow, 1, numRows, colCount).getValues();
       
       const totalIdx = headers.indexOf("Total");
       const itemsIdx = headers.indexOf("Items");
       
       let totalOmzet = 0;
       let totalQty = 0;
       
       for (let row of data) {
           if (totalIdx !== -1) totalOmzet += (parseFloat(row[totalIdx]) || 0);
           if (itemsIdx !== -1 && row[itemsIdx]) {
               try {
                   let items = typeof row[itemsIdx] === 'string' ? JSON.parse(row[itemsIdx]) : row[itemsIdx];
                   if (Array.isArray(items)) {
                       for (let item of items) {
                           totalQty += (parseInt(item.quantity) || 0);
                       }
                   }
               } catch(e) {}
           }
       }
       return { totalOmzet: Math.round(totalOmzet * 100) / 100, totalQty };
    };
    
    const srcAgg = calculateAggregates(srcSheet, 2, srcRowCount - 1, srcColCount, srcHeader);
    
    // The copied data should be the last (srcRowCount - 1) rows in destSheet
    const destStartRow = destRowCount - (srcRowCount - 1) + 1;
    const destAgg = calculateAggregates(destSheet, destStartRow, srcRowCount - 1, srcColCount, destHeader);
    
    if (srcAgg.totalOmzet !== destAgg.totalOmzet || srcAgg.totalQty !== destAgg.totalQty) {
      return false;
    }
    
    return true;
  }

  static archiveOldTransactions(isDryRun = false) {
    try {
      const config = AppConfig.getAppConfig();
      const invService = new InvoiceService();
      const histService = new BaseSpreadsheetService("HISTORY");
      
      const invSS = invService.getSpreadsheet();
      const histSS = histService.getSpreadsheet();
      
      const sheets = invSS.getSheets();
      const now = new Date();
      
      const cutoffMonth = new Date(now.getFullYear(), now.getMonth() - (config.archiveKeepMonths - 1), 1);
      const props = PropertiesService.getScriptProperties();
      const CHUNK_SIZE = 1000;
      const startTime = new Date().getTime();
      const MAX_EXECUTION_TIME = 250000; // ~4.1 mins
      
      let processedAny = false;
      let dryRunResults = [];
      
      for (let sheet of sheets) {
        let name = sheet.getName();
        if (name.startsWith("Transactions_")) {
          let parts = name.split("_");
          if (parts.length === 3) {
            let sheetDate = new Date(parseInt(parts[1]), parseInt(parts[2]) - 1, 1);
            if (sheetDate < cutoffMonth) {
              processedAny = true;
              let destSheetName = `Trx_${parts[2]}_${parts[1]}`;
              const srcRowCount = sheet.getLastRow();
              const srcColCount = sheet.getLastColumn();
              
              if (isDryRun) {
                 dryRunResults.push({
                   sourceSheet: name,
                   destinationSheet: destSheetName,
                   rowCount: srcRowCount,
                   status: srcRowCount <= 1 ? "Will be deleted (Empty)" : "Will be archived"
                 });
                 continue;
              }

              if (srcRowCount <= 1) {
                invSS.deleteSheet(sheet);
                Logger.logArchive(destSheetName, "SUCCESS", 0, "Empty sheet deleted.");
                continue;
              }
              
              let destSheet = this.ensureSheetExists(histSS, destSheetName, sheet, srcColCount);
              
              const stateKey = "ARCHIVE_STATE_" + destSheetName;
              let stateStr = props.getProperty(stateKey);
              let state = stateStr ? JSON.parse(stateStr) : { lastProcessedRow: 1 };
              
              let startRow = state.lastProcessedRow + 1;
              let timeoutReached = false;
              
              while (startRow <= srcRowCount) {
                if (new Date().getTime() - startTime > MAX_EXECUTION_TIME) {
                  timeoutReached = true;
                  break;
                }
                
                let numRows = Math.min(CHUNK_SIZE, srcRowCount - startRow + 1);
                let data = sheet.getRange(startRow, 1, numRows, srcColCount).getValues();
                
                let destLastRow = destSheet.getLastRow();
                destSheet.getRange(destLastRow + 1, 1, numRows, srcColCount).setValues(data);
                
                state.lastProcessedRow = startRow + numRows - 1;
                state.lastRun = new Date().toISOString();
                props.setProperty(stateKey, JSON.stringify(state));
                
                startRow = state.lastProcessedRow + 1;
              }
              
              if (timeoutReached) {
                Logger.logArchive(destSheetName, "PENDING", srcRowCount, "Timeout reached, progress saved at row " + state.lastProcessedRow);
                return { status: "pending", message: "Timeout reached, progress saved at row " + state.lastProcessedRow };
              }
              
              const isVerified = this.verifyArchive(sheet, destSheet, srcRowCount, srcColCount);
              
              if (isVerified) {
                invSS.deleteSheet(sheet);
                props.deleteProperty(stateKey);
                Logger.logArchive(destSheetName, "SUCCESS", srcRowCount, "Archived & deleted from invoice DB using chunking.");
                Logger.logAction("archiveOldTransactions", `Archived ${name} to ${destSheetName} & Deleted original`);
                
                // Update Metrics
                const duration = new Date().getTime() - startTime;
                ArchiveService.updateArchiveMetrics(duration, srcRowCount, destSheetName);
              } else {
                const destRowCount = destSheet.getLastRow();
                const destColCount = destSheet.getLastColumn();
                Logger.logArchive(destSheetName, "FAILED", srcRowCount, `Verification failed. Src: ${srcRowCount}x${srcColCount}, Dest: ${destRowCount}x${destColCount}`);
                Logger.logError(new Error("Verification failed after copy"), "Archive - " + destSheetName);
              }
            }
          }
        }
      }
      
      if (isDryRun) {
        return { status: "success", message: processedAny ? "Dry run selesai." : "Tidak ada sheet lama untuk diarsipkan.", data: dryRunResults };
      }
      
      return { status: "success", message: processedAny ? "Proses arsip selesai." : "Tidak ada sheet lama untuk diarsipkan." };
    } catch (e) {
      Logger.logError(e, "archiveOldTransactions");
      return { status: "error", message: e.toString() };
    }
  }

  static updateArchiveMetrics(duration, rowCount, sheetName) {
    try {
      const props = PropertiesService.getScriptProperties();
      
      let avgDur = props.getProperty("METRIC_AvgDuration") || 0;
      let count = props.getProperty("METRIC_ArchiveCount") || 0;
      
      avgDur = parseFloat(avgDur);
      count = parseInt(count);
      
      let newAvg = ((avgDur * count) + duration) / (count + 1);
      
      props.setProperty("METRIC_AvgDuration", newAvg);
      props.setProperty("METRIC_ArchiveCount", count + 1);
      
      Logger.updateSystemMetrics("AverageDuration", Math.round(newAvg) + " ms");
      Logger.updateSystemMetrics("LastDuration", duration + " ms");
      Logger.updateSystemMetrics("LastArchiveSuccess", new Date().toISOString());
      
      let maxRows = props.getProperty("METRIC_LargestRows") || 0;
      maxRows = parseInt(maxRows);
      if (rowCount > maxRows) {
        props.setProperty("METRIC_LargestRows", rowCount);
        props.setProperty("METRIC_LargestMonth", sheetName);
        Logger.updateSystemMetrics("LargestRows", rowCount);
        Logger.updateSystemMetrics("LargestMonth", sheetName);
      }
    } catch(e) {}
  }
}

class HealthService {
  static check() {
    const CACHE_KEY = "HEALTH_CHECK_DATA";
    
    // Utilize getWithLock to prevent stampede for dashboard refreshes
    let resultStr = CacheUtil.getWithLock(CACHE_KEY, () => {
      const startTime = new Date().getTime();
      const config = AppConfig.getAppConfig();
      const props = PropertiesService.getScriptProperties();
      const keys = props.getKeys();
      
      let archiveState = [];
      let pendingArchiveCount = 0;
      let lastUpdate = null;
      let uploadQueueCount = parseInt(props.getProperty("UPLOAD_QUEUE_COUNT") || "0");

      for (let key of keys) {
        if (key.startsWith("ARCHIVE_STATE_")) {
          try {
            const state = JSON.parse(props.getProperty(key));
            archiveState.push({
              sheetName: key.replace("ARCHIVE_STATE_", ""),
              lastProcessedRow: state.lastProcessedRow,
              lastRun: state.lastRun
            });
            pendingArchiveCount++;
            if (!lastUpdate || new Date(state.lastRun) > new Date(lastUpdate)) {
              lastUpdate = state.lastRun;
            }
          } catch(e) {}
        }
      }

      let archiveTriggerMissing = true;
      try {
        let triggers = ScriptApp.getProjectTriggers();
        for (let t of triggers) {
          if (t.getHandlerFunction() === "runMonthlyArchiving") {
            archiveTriggerMissing = false;
            break;
          }
        }
      } catch(e) {}
      
      const triggerLastRun = props.getProperty("TRIGGER_LAST_RUN") || "Never";
      const triggerLastStatus = props.getProperty("TRIGGER_LAST_STATUS") || "Unknown";

      let dbs = {};
      for (let key in CONFIG.DB) {
        try {
          let ss = SPREADSHEET_CACHE[key] || SpreadsheetApp.openById(CONFIG.DB[key]); SPREADSHEET_CACHE[key] = ss;
          let size = 0;
          try { size = DriveApp.getFileById(CONFIG.DB[key]).getSize(); } catch(e) {}
          dbs[key] = { status: "OK", name: ss.getName(), sizeBytes: size };
        } catch(e) {
          dbs[key] = { status: "ERROR", message: e.message };
        }
      }
      
      const metrics = Logger.getSystemMetrics();
      metrics.PendingArchive = pendingArchiveCount;
      metrics.PendingUpload = uploadQueueCount;

      const cacheStats = CacheUtil.getStats();
      const totalCache = cacheStats.hit + cacheStats.miss;
      const cacheHitRate = totalCache > 0 ? Math.round((cacheStats.hit / totalCache) * 100) + "%" : "0%";

      let todayDate = new Date().toISOString().substring(0,10);
      let todayKey = "TRX_TODAY_" + todayDate;
      const todayTransactions = parseInt(props.getProperty(todayKey) || "0");
      const syncErrors = parseInt(props.getProperty("SYNC_ERROR_COUNT") || "0");

      const duration = new Date().getTime() - startTime;
      Logger.logPerformance("healthCheck", duration, "Requested health check");

      const result = {
        status: "OK",
        timestamp: new Date().toISOString(),
        config: config,
        archiveStatus: archiveState,
        lastUpdate: lastUpdate || metrics.LastArchiveSuccess || "N/A",
        todayTransactions: todayTransactions,
        syncErrors: syncErrors,
        databaseHealth: dbs,
        systemMetrics: metrics,
        triggerHealth: {
          archiveTriggerMissing: archiveTriggerMissing,
          lastRun: triggerLastRun,
          lastStatus: triggerLastStatus
        },
        cacheHealth: {
          hitRate: cacheHitRate,
          hits: cacheStats.hit,
          misses: cacheStats.miss
        },
        recentArchiveLog: Logger.getLastArchiveLog()
      };
      
      return JSON.stringify(result);
    }, 30); // 30 seconds cache

    return JSON.parse(resultStr);
  }
}

class SyncService {
  static handleGet(e) {
    const action = e.parameter.action;
    const startTime = new Date().getTime();
    try {
      if (action === "getInitialData") {
        return ContentService.createTextOutput(new MasterService().getInitialData(e.parameter.lastSync, e.parameter.clientVersion)).setMimeType(ContentService.MimeType.JSON);
      } else       if (action === "login" || action === "getUsers") {
        return ContentService.createTextOutput(new MasterService().getUsers()).setMimeType(ContentService.MimeType.JSON);
      } else if (action === "getProducts") {
        return ContentService.createTextOutput(new MasterService().getProducts()).setMimeType(ContentService.MimeType.JSON);
      } else if (action === "getOutlets") {
        return ContentService.createTextOutput(new MasterService().getOutlets()).setMimeType(ContentService.MimeType.JSON);
      } else if (action === "getTransactions") {
        const data = new InvoiceService().getTransactions(
          e.parameter.salesId, e.parameter.startDate, e.parameter.endDate, e.parameter.lastSync
        );
        const duration = new Date().getTime() - startTime;
        Logger.logPerformance("getTransactions", duration, `SalesId: ${e.parameter.salesId || "All"}`);
        return ResponseUtil.success(data);
      } else if (action === "getArchiveHistory") {
        const data = Logger.getArchiveHistory();
        return ResponseUtil.success(data);
      } else if (action === "healthCheck") {
        const data = HealthService.check();
        return ResponseUtil.success(data);
      }
      return ResponseUtil.error("Action not found");
    } catch(err) {
      Logger.logError(err, "doGet - " + action);
      return ResponseUtil.error(err.toString());
    }
  }
  
  static handlePost(e) {
    const startTime = new Date().getTime();
    const config = AppConfig.getAppConfig();
    const lock = LockService.getScriptLock();
    const props = PropertiesService.getScriptProperties();
    
    if (!lock.tryLock(config.lockTimeout)) {
      return ResponseUtil.error("Sistem sedang sibuk. Silakan coba lagi nanti.");
    }
    
    let action = "unknown";
    try {
      const data = JSON.parse(decodePostBody(e));
      action = data.action;
      
      if (data.deviceId || data.appVersion) {
        Logger.logDevice(data.deviceId, data.appVersion, data.salesId, action);
      }
      
      if (action === "syncTransactions") {
        if (data.printerSettings) {
          SyncService.savePrinterSettings(data.printerSettings);
        }
        const invoiceService = new InvoiceService();
        
        // Simulasikan penambahan ke queue
        let currentQueue = parseInt(props.getProperty("UPLOAD_QUEUE_COUNT") || "0");
        let transactionsReceived = (data.transactions || []).length;
        props.setProperty("UPLOAD_QUEUE_COUNT", (currentQueue + transactionsReceived).toString());

        const result = invoiceService.bulkSaveTransactions(data.transactions || []);
        
        
        if (data.syncUsers && data.syncUsers.length > 0) {
          const masterService = new MasterService();
          const userSheet = masterService.getSheet("Users");
          if (userSheet) {
            let lastRow = userSheet.getLastRow();
            if (data.isUserAdmin) {
              let sentIds = {};
              data.syncUsers.forEach(u => { sentIds[u.id] = true; });
              if (lastRow > 1) {
                for (let i = lastRow; i >= 2; i--) {
                  let rowId = userSheet.getRange(i, 1).getValue();
                  let username = userSheet.getRange(i, 2).getValue();
                  if (rowId && rowId !== "ADM-01" && String(username).toLowerCase() !== "admin" && !sentIds[rowId]) {
                    userSheet.deleteRow(i);
                  }
                }
              }
              lastRow = userSheet.getLastRow();
            }
            
            let existingUserIds = {};
            let existingRowIndex = {};
            if (lastRow > 1) {
               let ids = userSheet.getRange(2, 1, lastRow - 1, 1).getValues();
               ids.forEach((row, i) => { if (row[0]) { existingUserIds[row[0]] = true; existingRowIndex[row[0]] = i + 2; } });
            }
            let newUserRows = [];
            data.syncUsers.forEach(u => {
               if (existingUserIds[u.id]) {
                   let rowIndex = existingRowIndex[u.id];
                   userSheet.getRange(rowIndex, 2, 1, 3).setValues([[u.username, u.password, u.role]]);
               } else {
                   newUserRows.push([
                      u.id,
                      u.username,
                      u.password,
                      u.role
                   ]);
                   existingUserIds[u.id] = true;
               }
            });
            if (newUserRows.length > 0) {
               userSheet.getRange(lastRow + 1, 1, newUserRows.length, newUserRows[0].length).setValues(newUserRows);
            }
          }
        }
        
        if (data.syncProducts && data.syncProducts.length > 0) {
          const masterService = new MasterService();
          const pSheet = masterService.getSheet("Products");
          if (pSheet) {
            let lastRow = pSheet.getLastRow();
            if (data.isUserAdmin) {
              let sentIds = {};
              data.syncProducts.forEach(p => { sentIds[p.id] = true; });
              if (lastRow > 1) {
                for (let i = lastRow; i >= 2; i--) {
                  let rowId = pSheet.getRange(i, 1).getValue();
                  if (rowId && !sentIds[rowId]) {
                    pSheet.deleteRow(i);
                  }
                }
              }
              lastRow = pSheet.getLastRow();
            }
            
            let existingIds = {};
            let existingRowIndex = {};
            if (lastRow > 1) {
               let ids = pSheet.getRange(2, 1, lastRow - 1, 1).getValues();
               ids.forEach((row, i) => { if (row[0]) { existingIds[row[0]] = true; existingRowIndex[row[0]] = i + 2; } });
            }
            let newRows = [];
            data.syncProducts.forEach(p => {
               let priceR = p.priceRetail || p.PriceRetail || 0;
               let priceW = p.priceWholesale || p.PriceWholesale || 0;
               let stockVal = p.warehouseStock || p.WarehouseStock || p.stock || p.Stock || 0;
               if (existingIds[p.id]) {
                   let rowIndex = existingRowIndex[p.id];
                   pSheet.getRange(rowIndex, 2, 1, 4).setValues([[p.name, priceR, priceW, stockVal]]);
               } else {
                   newRows.push([
                      p.id,
                      p.name,
                      priceR,
                      priceW,
                      stockVal
                   ]);
                   existingIds[p.id] = true;
               }
            });
            if (newRows.length > 0) {
               pSheet.getRange(lastRow + 1, 1, newRows.length, newRows[0].length).setValues(newRows);
            }
          }
        }
        
        if (data.isUserAdmin && data.syncStokis && data.syncStokis.length > 0) {
          const masterService = new MasterService();
          const stokisSheet = masterService.getSheet("Stokis");
          if (stokisSheet) {
            let lastRow = stokisSheet.getLastRow();
            let sentIds = {};
            data.syncStokis.forEach(s => { sentIds[s.id] = true; });
            if (lastRow > 1) {
              for (let i = lastRow; i >= 2; i--) {
                let rowId = stokisSheet.getRange(i, 1).getValue();
                if (rowId && !sentIds[rowId]) {
                  stokisSheet.deleteRow(i);
                }
              }
            }
            lastRow = stokisSheet.getLastRow();
            
            let existingStokisIds = {};
            let existingRowIndex = {};
            if (lastRow > 1) {
               let ids = stokisSheet.getRange(2, 1, lastRow - 1, 1).getValues();
               ids.forEach((row, i) => { if (row[0]) { existingStokisIds[row[0]] = true; existingRowIndex[row[0]] = i + 2; } });
            }
            let newStokisRows = [];
            data.syncStokis.forEach(s => {
               if (existingStokisIds[s.id]) {
                   let rowIndex = existingRowIndex[s.id];
                   stokisSheet.getRange(rowIndex, 2, 1, 4).setValues([[s.name, s.address, s.assignedSalesId, s.assignedSalesName]]);
               } else {
                   newStokisRows.push([
                      s.id,
                      s.name,
                      s.address,
                      s.assignedSalesId,
                      s.assignedSalesName
                   ]);
                   existingStokisIds[s.id] = true;
               }
            });
            if (newStokisRows.length > 0) {
               stokisSheet.getRange(lastRow + 1, 1, newStokisRows.length, newStokisRows[0].length).setValues(newStokisRows);
            }
          }
        }
        
        if (data.isUserAdmin && data.syncOutlets && data.syncOutlets.length > 0) {
          const masterService = new MasterService();
          const outletSheet = masterService.getSheet("Outlets");
          if (outletSheet) {
            let lastRow = outletSheet.getLastRow();
            let sentIds = {};
            data.syncOutlets.forEach(o => { sentIds[o.id] = true; });
            if (lastRow > 1) {
              for (let i = lastRow; i >= 2; i--) {
                let rowId = outletSheet.getRange(i, 1).getValue();
                if (rowId && !sentIds[rowId]) {
                  outletSheet.deleteRow(i);
                }
              }
            }
            lastRow = outletSheet.getLastRow();
            
            let existingOutletIds = {};
            let existingRowIndex = {};
            if (lastRow > 1) {
               let ids = outletSheet.getRange(2, 1, lastRow - 1, 1).getValues();
               ids.forEach((row, i) => { if (row[0]) { existingOutletIds[row[0]] = true; existingRowIndex[row[0]] = i + 2; } });
            }
            let newOutletRows = [];
            data.syncOutlets.forEach(o => {
               if (existingOutletIds[o.id]) {
                  let rowIndex = existingRowIndex[o.id];
                  outletSheet.getRange(rowIndex, 2, 1, 8).setValues([[
                     o.name,
                     o.address,
                     o.type,
                     o.category,
                     o.geotag || "",
                     o.salesId || "",
                     o.salesName || "",
                     o.kodeHari || ""
                  ]]);
               } else {
                  newOutletRows.push([
                     o.id,
                     o.name,
                     o.address,
                     o.type,
                     o.category,
                     o.geotag || "",
                     o.salesId || "",
                     o.salesName || "",
                     o.kodeHari || ""
                  ]);
                  existingOutletIds[o.id] = true;
               }
            });
            if (newOutletRows.length > 0) {
               outletSheet.getRange(lastRow + 1, 1, newOutletRows.length, newOutletRows[0].length).setValues(newOutletRows);
            }
          }
        } else if (data.newOutlets && data.newOutlets.length > 0) {
          const masterService = new MasterService();
          const outletSheet = masterService.getSheet("Outlets");
          if (outletSheet) {
            let lastRow = outletSheet.getLastRow();
            let existingOutletIds = {};
            let existingOutletRowIndex = {};
            if (lastRow > 1) {
               let ids = outletSheet.getRange(2, 1, lastRow - 1, 1).getValues();
               ids.forEach((row, i) => { if (row[0]) { existingOutletIds[row[0]] = true; existingOutletRowIndex[row[0]] = i + 2; } });
            }
            let newOutletRows = [];
            data.newOutlets.forEach(o => {
               if (existingOutletIds[o.id]) {
                 let rowIndex = existingOutletRowIndex[o.id];
                 outletSheet.getRange(rowIndex, 2, 1, 8).setValues([[
                    o.name,
                    o.address,
                    o.type,
                    o.category,
                    o.geotag || "",
                    o.salesId || "",
                    o.salesName || "",
                    o.kodeHari || ""
                 ]]);
               } else {
                 newOutletRows.push([
                    o.id,
                    o.name,
                    o.address,
                    o.type,
                    o.category,
                    o.geotag || "",
                    o.salesId || "",
                    o.salesName || "",
                    o.kodeHari || ""
                 ]);
                 existingOutletIds[o.id] = true;
               }
            });
            if (newOutletRows.length > 0) {
               outletSheet.getRange(lastRow + 1, 1, newOutletRows.length, newOutletRows[0].length).setValues(newOutletRows);
            }
          }
        }
        
        if (result.validTransactions.length > 0) {
          const summaryService = new SummaryService();
          summaryService.updateDashboard(result.validTransactions);
        }
        
        // Simulasikan pemrosesan dari queue
        currentQueue = parseInt(props.getProperty("UPLOAD_QUEUE_COUNT") || "0");
        let remainingQueue = Math.max(0, currentQueue - transactionsReceived);
        props.setProperty("UPLOAD_QUEUE_COUNT", remainingQueue.toString());

        const duration = new Date().getTime() - startTime;
        Logger.logAction("syncTransactions", `Success: ${result.insertedCount} inserted, ${result.skippedCount} skipped. AppVer: ${data.appVersion || "Unknown"}`);
        Logger.logPerformance("syncTransactions", duration, `Inserted: ${result.insertedCount}`);
        
        if (data.isUserAdmin || (data.newOutlets && data.newOutlets.length > 0)) {
          const version = parseInt(props.getProperty("MASTER_DATA_VERSION") || "1");
          props.setProperty("MASTER_DATA_VERSION", "v" + (version + 1).toString());
        }
        
        invalidateAllCaches();
        
        return ResponseUtil.success({
          status: "success",
          message: "Sinkronisasi selesai.",
          inserted: result.insertedCount,
          skipped_duplicate: result.skippedCount
        });
      } else if (action === "pullStockFromStokis") {
        // PERBAIKAN BUG (fitur "Ambil Stock Mandiri dari Stokis" di Android tidak
        // benar-benar tersimpan -- stock yang sudah ditarik sales kembali ke jumlah
        // semula / "tidak bisa di-loading mandiri"): sebelumnya fitur tarik-stock-mandiri
        // di aplikasi Android HANYA mengubah database Room LOKAL (mengurangi stok stokis
        // & menambah alokasi stok sales secara lokal saja), lalu memanggil sinkronisasi
        // umum yang TIDAK PERNAH mengirim perubahan StokisStock ke server sama sekali.
        // Akibatnya: server tidak pernah tahu stok sudah ditarik, sehingga sinkronisasi
        // berikutnya (yang berjalan tepat setelah aksi tarik ini) mengunduh ulang angka
        // stok stokis yang MASIH UTUH dari server dan menimpa balik perubahan lokal --
        // seolah-olah stock yang sudah dikirim ke stokis "tidak bisa" dimuat mandiri oleh
        // sales. Route baru ini memakai fungsi injectFromStokisToSales yang SUDAH ADA
        // (sebelumnya hanya bisa dipanggil dari console Admin), supaya aplikasi Android
        // juga bisa memicu pengurangan ledger StokisStock + penambahan StockAllocations
        // langsung di server, sama seperti saat Admin melakukannya dari console web.
        const result = injectFromStokisToSales(data.stokisId, data.salesName, data.payload || []);
        return ResponseUtil.success(result);
      } else if (action === "archiveNow" || action === "retryArchive") {
        const result = ArchiveService.archiveOldTransactions(false);
        const duration = new Date().getTime() - startTime;
        Logger.logPerformance(action, duration, `Status: ${result.status}`);
        return ResponseUtil.success(result);
      } else if (action === "dryRunArchive") {
        const result = ArchiveService.archiveOldTransactions(true);
        const duration = new Date().getTime() - startTime;
        Logger.logPerformance(action, duration, `Status: ${result.status}`);
        return ResponseUtil.success(result);
      }
      
      return ResponseUtil.error("Action not found");
    } catch(err) {
      Logger.logError(err, "doPost - " + action);
      return ResponseUtil.error(err.toString());
    } finally {
      lock.releaseLock();
    }
  }

  static savePrinterSettings(printerSettings) {
    if (!printerSettings) return;
    try {
      const masterService = new MasterService();
      const settingSheet = masterService.getSheet("Setting");
      if (settingSheet) {
        let lastRow = settingSheet.getLastRow();
        let existingKeys = {};
        let existingRowIndex = {};
        if (lastRow > 1) {
           let keys = settingSheet.getRange(2, 1, lastRow - 1, 1).getValues();
           keys.forEach((row, i) => { if (row[0]) { existingKeys[row[0]] = true; existingRowIndex[row[0]] = i + 2; } });
        }
        
        const updates = {
          "PRINTER_HEADER": printerSettings.header || "",
          "PRINTER_FOOTER": printerSettings.footer || "",
          "PRINTER_HEADER_ALIGN": printerSettings.headerAlign || "Center",
          "PRINTER_FOOTER_ALIGN": printerSettings.footerAlign || "Center",
          "PRINTER_LOGO_BASE64": printerSettings.logoBase64 || "",
          "PRINTER_LOGO_ALIGN": printerSettings.logoAlign || "Center"
        };
        
        for (let key in updates) {
          let val = updates[key];
          if (existingKeys[key]) {
            let rowIndex = existingRowIndex[key];
            settingSheet.getRange(rowIndex, 2).setValue(val);
          } else {
            settingSheet.appendRow([key, val]);
            lastRow++;
            existingKeys[key] = true;
            existingRowIndex[key] = lastRow;
          }
        }
      }
    } catch(e) {
      Logger.logError(e, "savePrinterSettings");
    }
  }
}

function setupDatabase() {
  const results = [];
  const sheetsToCreate = [
    { dbKey: "USERS", name: "Users", headers: ["ID", "Username", "Password", "Role"] },
    { dbKey: "PRODUCTS", name: "Products", headers: ["ID", "Name", "Retail", "Wholesale", "Stock"] },
    { dbKey: "STOKIS", name: "Stokis", headers: ["ID", "Name", "Address", "SalesAuthority"] },
    { dbKey: "STOKIS_STOCK", name: "StokisStock", headers: ["StokisID", "ProductID", "ProductName", "Qty", "Date"] },
    { dbKey: "STOCK_ALLOCATIONS", name: "StockAllocations", headers: ["Sales", "ProductID", "Product", "Qty", "Date"] },
    { dbKey: "OUTLETS", name: "InjectOutlets", headers: ["Sales", "Day", "Outlet", "Date"] },
    { dbKey: "OUTLETS", name: "Outlets", headers: ["ID", "Name", "Address", "Type", "PriceTier", "Geotag", "SalesID", "SalesName", "KodeHari"] },
    { dbKey: "RECEIPT_SETTINGS", name: "Setting", headers: ["Key", "Value"] },
    { dbKey: "REPORTS", name: "SalesSummary", headers: ["SalesId", "SalesName", "TotalOmzet", "TotalTrx"] },
    { dbKey: "REPORTS", name: "ProductSummary", headers: ["ProductId", "ProductName", "TotalQty", "TotalOmzet"] },
    { dbKey: "SYNC_LOGS", name: "DeviceLog", headers: ["Timestamp", "DeviceId", "AppVersion", "SalesId", "Action"] },
    { dbKey: "SYNC_LOGS", name: "ErrorLog", headers: ["Timestamp", "Context", "ErrorMessage", "Stack"] },
    { dbKey: "SYNC_LOGS", name: "SyncLog", headers: ["Timestamp", "Action", "Description"] },
    { dbKey: "SYNC_LOGS", name: "ArchiveLog", headers: ["Timestamp", "SheetName", "Status", "RowCount", "Message"] },
    { dbKey: "SYNC_LOGS", name: "PerformanceLog", headers: ["Timestamp", "Action", "DurationMs", "Details"] },
    { dbKey: "SYNC_LOGS", name: "SystemMetrics", headers: ["Key", "Value"] }
  ];

  // Also include Transactions template for current month
  try {
    const currentMonth = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyyMM");
    sheetsToCreate.push({
      dbKey: "TRANSACTIONS",
      name: "Transactions_" + currentMonth,
      headers: ["OrderId", "Date", "SalesId", "SalesName", "OutletId", "OutletName", "OutletType", "Geotag", "Total", "PaymentMethod", "Items", "UploadedAt"]
    });
  } catch(e) {
    sheetsToCreate.push({
      dbKey: "TRANSACTIONS",
      name: "Transactions_202607", // Default fallback
      headers: ["OrderId", "Date", "SalesId", "SalesName", "OutletId", "OutletName", "OutletType", "Geotag", "Total", "PaymentMethod", "Items", "UploadedAt"]
    });
  }

  for (let s of sheetsToCreate) {
    try {
      const spreadSheetId = CONFIG.DB[s.dbKey];
      let ss = null;
      if (spreadSheetId) {
        try {
          ss = SpreadsheetApp.openById(spreadSheetId);
        } catch (err) {
          // Fallback to CONFIG.DB.USERS
          try {
            if (CONFIG.DB.USERS) ss = SpreadsheetApp.openById(CONFIG.DB.USERS);
          } catch (err2) {
            // Fallback to Active Spreadsheet
            try {
              ss = SpreadsheetApp.getActiveSpreadsheet();
            } catch (err3) {
              ss = null;
            }
          }
        }
      }
      if (!ss) {
        try {
          ss = SpreadsheetApp.getActiveSpreadsheet();
        } catch (err) {
          results.push(`Config DB untuk ${s.dbKey} tidak ditemukan/tidak dapat dibuka dan no fallback`);
          continue;
        }
      }

      let sheet = ss.getSheetByName(s.name);
      if (!sheet) {
        sheet = ss.insertSheet(s.name);
        sheet.appendRow(s.headers);
        results.push(`Sheet '${s.name}' berhasil dibuat di DB ${s.dbKey} (atau fallback)`);
      } else {
        // Ensure headers if empty
        if (sheet.getLastRow() === 0) {
          sheet.appendRow(s.headers);
          results.push(`Sheet '${s.name}' sudah ada, header ditambahkan`);
        } else {
          results.push(`Sheet '${s.name}' sudah siap`);
        }
      }
    } catch (e) {
      results.push(`Gagal memproses '${s.name}' di DB ${s.dbKey}: ${e.toString()}`);
    }
  }
  
  // Create a default admin user if USERS is empty or doesn't have any users
  try {
    let ssUsers = null;
    try {
      ssUsers = SpreadsheetApp.openById(CONFIG.DB.USERS);
    } catch (err) {
      try {
        ssUsers = SpreadsheetApp.getActiveSpreadsheet();
      } catch (err2) {
        ssUsers = null;
      }
    }
    const sheetUsers = ssUsers ? ssUsers.getSheetByName("Users") : null;
    if (sheetUsers) {
      // Check if "admin" username already exists to avoid duplication
      let lastRow = sheetUsers.getLastRow();
      let lastCol = sheetUsers.getLastColumn();
      let data = (lastRow > 0 && lastCol > 0) ? sheetUsers.getRange(1, 1, lastRow, lastCol).getValues() : [];
      let hasAdmin = false;
      for (let i = 1; i < data.length; i++) {
        if (String(data[i][1]).toLowerCase().trim() === "admin") {
          hasAdmin = true;
          break;
        }
      }
      if (!hasAdmin) {
        sheetUsers.appendRow(["USR-1", "admin", "admin", "Super Admin"]);
        results.push("Default user admin/admin berhasil dibuat.");
      } else {
        results.push("User admin sudah terdaftar di sheet.");
      }
    }
  } catch(e) {
    results.push(`Gagal membuat default user admin: ${e.toString()}`);
  }

  return { success: true, log: results };
}

// =========================================================================
// MAIN ENDPOINTS (doGet, doPost, Scheduled Triggers)
// =========================================================================

function doGet(e) {
  // Automatically initialize database sheets if not already created
  try {
    setupDatabase();
  } catch (err) {
    if (typeof Logger !== 'undefined' && Logger.log) {
      Logger.log("Auto-setup database failed: " + err.toString());
    }
  }

  if (e && e.parameter && e.parameter.action) {
    return SyncService.handleGet(e);
  }
  // Serve Web App UI if no action is specified
  try {
    return HtmlService.createTemplateFromFile('Index')
      .evaluate()
      .setTitle('SFA Console')
      .addMetaTag('viewport', 'width=device-width, initial-scale=1');
  } catch (err) {
    try {
      return HtmlService.createTemplateFromFile('index')
        .evaluate()
        .setTitle('SFA Console')
        .addMetaTag('viewport', 'width=device-width, initial-scale=1');
    } catch(err2) {
      return ContentService.createTextOutput("Gagal memuat frontend. Kemungkinan nama file bukan Index.html atau ada error di HTML. Error detail: " + err.message + " | " + err2.message);
    }
  }
}

function doPost(e) {
  return SyncService.handlePost(e);
}

function runMonthlyArchiving() {
  const props = PropertiesService.getScriptProperties();
  try {
    props.setProperty("TRIGGER_LAST_RUN", new Date().toISOString());
    ArchiveService.archiveOldTransactions(false);
    props.setProperty("TRIGGER_LAST_STATUS", "SUCCESS");
  } catch (e) {
    props.setProperty("TRIGGER_LAST_STATUS", "FAILED - " + e.message);
  }
}

// =========================================================================
// WRAPPER FUNCTIONS FOR FRONTEND WEB APP
// =========================================================================

function attemptLogin(username, password) {
  try {
    // PERBAIKAN BUG (Admin yang baru ditambahkan/diedit LANGSUNG di spreadsheet
    // Google Sheets -- bukan lewat menu "Tambah User" di console -- tidak bisa
    // login): sebelumnya attemptLogin() memanggil ms.getUsers() yang membaca dari
    // CACHE selama 5 menit (CONFIG.CACHE_EXPIRATION = 300 detik). Kalau baris user
    // diedit manual langsung di sheet, Apps Script TIDAK TAHU sheet-nya berubah,
    // jadi cache lama (yang belum berisi/belum sesuai baris user tsb) tetap dipakai
    // sampai 5 menit berlalu -- membuat login gagal padahal datanya sudah benar di
    // spreadsheet. Login adalah aksi yang jarang terjadi & sangat sensitif terhadap
    // kebenaran data, jadi sekarang dibaca LANGSUNG dari sheet (tanpa cache),
    // memastikan perubahan manual di spreadsheet langsung berlaku saat itu juga.
    const ms = new MasterService();
    const uSheet = ms.getSheet("Users");
    const userList = uSheet ? SheetUtil.getDataAsObjects(uSheet) : [];
    const user = userList.find(u => String(u.username || u.Username).trim() === String(username).trim() && String(u.password || u.Password).trim() === String(password).trim());
    
    if (user) {
      return { success: true, user: user };
    }
    if (String(username).trim().toLowerCase() === "admin" && String(password).trim() === "admin") {
      return { success: true, user: { username: "admin", Username: "admin", role: "Super Admin", Role: "Super Admin", id: "0", ID: "0" } };
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
    
    const oSheet = ms.getSheet("Outlets");
    const outlets = oSheet ? SheetUtil.getDataAsObjects(oSheet) : [];

    const settingSheet = ms.getSheet("Setting");
    const settingsRows = settingSheet ? SheetUtil.getDataAsObjects(settingSheet) : [];
    const printerSettings = { header: "", footer: "", headerAlign: "Center", footerAlign: "Center", logoBase64: "", logoAlign: "Center" };
    settingsRows.forEach(row => {
      const k = row.Key || row.key || "";
      const v = row.Value || row.value || "";
      if (k === "PRINTER_HEADER") printerSettings.header = v;
      if (k === "PRINTER_FOOTER") printerSettings.footer = v;
      if (k === "PRINTER_HEADER_ALIGN") printerSettings.headerAlign = v;
      if (k === "PRINTER_FOOTER_ALIGN") printerSettings.footerAlign = v;
      if (k === "PRINTER_LOGO_BASE64") printerSettings.logoBase64 = v;
      if (k === "PRINTER_LOGO_ALIGN") printerSettings.logoAlign = v;
    });
    
    return { success: true, appName: SpreadsheetApp.getActiveSpreadsheet().getName(), users: users,
      products: products,
      stokisMaster: stokisMaster,
      outlets: outlets,
      printerSettings: printerSettings
    };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function savePrinterSettingsFromWeb(settingsObj) {
  try {
    SyncService.savePrinterSettings(settingsObj);
    return { success: true, message: "Pengaturan printer berhasil disimpan." };
  } catch(e) {
    return { success: false, message: e.toString() };
  }
}

function fetchDynamicData() {
  try {
    const ms = new MasterService();
    const sss = new BaseSpreadsheetService("STOCK_SALES");
    
    // Check if InjectOutlets exists, else empty
    const injectOutlets = ms.getCachedValues("InjectOutlets");
    
    // InjectStock maps to StockAllocations in the backend
    const injectStock = sss.getCachedValues("StockAllocations");
    
    const stokisStock = sss.getCachedValues("StokisStock");
    
    const products = ms.getCachedValues("Products");
    const users = ms.getCachedValues("Users");
    const outlets = ms.getCachedValues("Outlets");
    const stokisMaster = ms.getCachedValues("Stokis");
    
    return {
      success: true,
      injectOutlets: injectOutlets,
      injectStock: injectStock,
      stockAllocations: injectStock, // unified name fallback
      stokisStock: stokisStock,
      products: products,
      users: users,
      outlets: outlets,
      stokisMaster: stokisMaster
    };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

// PERBAIKAN BUG KRITIS (login sukses tapi macet dengan error "Cannot read properties
// of null (reading 'message')"): sebelumnya fetchDynamicData() SELALU mengikutsertakan
// SELURUH data transaksi bulan berjalan (rawData) tanpa batas, dikirim ulang setiap kali
// login DAN setiap 15 detik lewat auto-sync. Kalau transaksi bulan berjalan sudah banyak
// (wajar setelah aplikasi dipakai beberapa minggu), gabungan payload JSON (transaksi +
// users + products + outlets + stock, dst) bisa melebihi batas ukuran response yang bisa
// ditangani jembatan google.script.run -- browser menerima response NULL walau fungsi
// backend-nya sendiri sukses tanpa error sama sekali. rawData sekarang dipisah ke fungsi
// tersendiri dan HANYA diambil saat benar-benar dibutuhkan (tab Riwayat/Laporan Sales,
// atau tab Raw Data Admin), bukan di setiap login/auto-sync -- payload utama jadi jauh
// lebih kecil dan tidak lagi rawan gagal.
function fetchRawTransactionsData() {
  try {
    const isv = new InvoiceService();
    const currentMonth = DateUtil.getYearMonth(new Date().toISOString());
    const rawData = isv.getValues("Transactions_" + currentMonth);
    return { success: true, rawData: rawData };
  } catch (err) {
    return { success: false, message: err.toString() };
  }
}

function fetchInitialData() {
  // Frontend might call this?
  return fetchStaticMaster();
}

// PERBAIKAN BUG (cocok dengan gejala audit "gagal update user/produk", turunan dari bug
// yang sama dengan "inject outlet gagal"): sebelumnya saveUser/deleteUser/saveProduct/
// deleteProduct memakai perbandingan strict "data[i][0] === id". Google Sheets sering
// otomatis mengubah nilai kolom ID yang murni angka (mis. "5") menjadi tipe Number,
// sedangkan ID yang dikirim dari Android SELALU berupa String. Akibatnya "5 === '5'"
// bernilai FALSE di Apps Script (JS), sehingga user/produk yang seharusnya di-UPDATE
// malah dianggap "tidak ditemukan" (baris baru duplikat ditambahkan, atau delete gagal
// total/"tidak ditemukan" padahal datanya ada). Sekarang disamakan dengan pola yang
// sudah dipakai di fungsi Outlet/Stokis: String(...).trim() di kedua sisi sebelum
// dibandingkan, supaya konsisten walau tipe data di sheet berbeda-beda antar baris.
function saveUser(id, username, role, password) {
  return executeWithLock(() => {
    try {
      if (!id || id.trim() === '') id = "USR-" + new Date().getTime();
      const ms = new MasterService();
      let sheet = ms.getSheet("Users");
      if (!sheet) {
         sheet = ms.insertSheet("Users");
         sheet.appendRow(["ID", "Username", "Password", "Role"]);
      }
      
      let lastRow = sheet.getLastRow();
      let isUpdate = false;
      
      if (lastRow > 1) {
        let data = sheet.getRange(2, 1, lastRow - 1, 4).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.getRange(i + 2, 2, 1, 3).setValues([[username, password, role]]);
            isUpdate = true;
            break;
          }
        }
      }
      
      if (!isUpdate) {
        sheet.appendRow([id, username, password, role]);
      }
      
      invalidateAllCaches(); return { success: true, message: "User berhasil disimpan" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function deleteUser(id) {
  return executeWithLock(() => {
    try {
      const ms = new MasterService();
      let sheet = ms.getSheet("Users");
      if (!sheet) {
         sheet = ms.insertSheet("Users");
         sheet.appendRow(["ID", "Username", "Password", "Role"]);
      }
      
      let lastRow = sheet.getLastRow();
      if (lastRow > 1) {
        let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.deleteRow(i + 2);
            invalidateAllCaches(); return { success: true, message: "User berhasil dihapus" };
          }
        }
      }
      return { success: false, message: "User tidak ditemukan" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function saveProduct(id, name, retail, wholesale, stock) {
  return executeWithLock(() => {
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
          if (id && String(data[i][0]).trim() === String(id).trim()) continue;
          if (String(data[i][1]).trim().toLowerCase() === String(name).trim().toLowerCase()) {
            return { success: false, message: "Gagal: Nama produk '" + name + "' sudah ada (duplikat)." };
          }
        }
      }
      
      let isUpdate = false;
      
      if (lastRow > 1) {
        let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.getRange(i + 2, 2, 1, 4).setValues([[name, retail, wholesale, stock]]);
            isUpdate = true;
            break;
          }
        }
      }
      
      if (!isUpdate) {
        sheet.appendRow([id, name, retail, wholesale, stock]);
      }
      
      invalidateAllCaches(); return { success: true, message: "Product berhasil disimpan" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function deleteProduct(id) {
  return executeWithLock(() => {
    try {
      const ms = new MasterService();
      let sheet = ms.getSheet("Products");
      if (!sheet) {
         sheet = ms.insertSheet("Products");
         sheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);
      }
      
      let lastRow = sheet.getLastRow();
      if (lastRow > 1) {
        let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.deleteRow(i + 2);
            invalidateAllCaches(); return { success: true, message: "Product berhasil dihapus" };
          }
        }
      }
      return { success: false, message: "Product tidak ditemukan" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function saveMasterStokis(id, name, address, sales) {
  return executeWithLock(() => {
    try {
      if (!id || id.trim() === '') id = "STK-" + new Date().getTime();
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
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.getRange(i + 2, 2, 1, 3).setValues([[name, address, sales]]);
            isUpdate = true;
            break;
          }
        }
      }
      
      if (!isUpdate) {
        sheet.appendRow([id, name, address, sales]);
      }
      
      invalidateAllCaches(); return { success: true, message: "Stokis berhasil disimpan" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function deleteMasterStokis(id) {
  return executeWithLock(() => {
    try {
      const ms = new MasterService();
      const sheet = ms.getSheet("Stokis");
      if (!sheet) return { success: false, message: "Sheet Stokis tidak ditemukan" };
      
      let lastRow = sheet.getLastRow();
      if (lastRow > 1) {
        let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.deleteRow(i + 2);
            invalidateAllCaches(); return { success: true, message: "Stokis berhasil dihapus" };
          }
        }
      }
      return { success: false, message: "Stokis tidak ditemukan" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function injectToStokis(targetId, payload) {
  return executeWithLock(() => {
    try {
      const sss = new BaseSpreadsheetService("STOCK_SALES");
      let sheet = sss.getSheet("StokisStock");
      if (!sheet) sheet = sss.insertSheet("StokisStock");
      
      if (sheet.getLastRow() === 0) {
        sheet.appendRow(["StokisID", "ProductID", "ProductName", "Qty", "Date"]);
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
           targetId,
           item.productId,
           pMap[item.productId] || item.productId, // name
           item.qty,
           dateStr
        ]);
      });
      
      if (rows.length > 0) {
        sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, 5).setValues(rows);
      }
      
      invalidateAllCaches(); return { success: true, message: "Stok berhasil di-inject ke Stokis" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

// FITUR BARU: Edit/Hapus item stok per Stokis dari menu "Daftar Stok per Stokis".
// StokisStock adalah sheet berbentuk LEDGER (setiap kali inject = baris baru
// ditambahkan), lalu di frontend semua baris dengan StokisID+ProductID yang sama
// dijumlahkan untuk ditampilkan sebagai satu angka total. Supaya "Edit" (ubah jadi
// angka tertentu) dan "Hapus" (kosongkan) konsisten dengan cara data ditampilkan,
// fungsi ini menghapus SEMUA baris ledger lama untuk StokisID+ProductID tsb, lalu
// -- jika newQty > 0 -- menambahkan satu baris baru berisi total yang benar. Dengan
// begitu total yang dihitung ulang oleh frontend akan selalu sama dengan newQty,
// tanpa perlu mengubah logika agregasi yang sudah ada di frontend/sync lainnya.
function updateStokisStockItem(stokisId, productId, newQty) {
  return executeWithLock(() => {
    try {
      const sss = new BaseSpreadsheetService("STOCK_SALES");
      const sheet = sss.getSheet("StokisStock");
      if (!sheet) return { success: false, message: "Sheet StokisStock tidak ditemukan" };

      const lastRow = sheet.getLastRow();
      if (lastRow <= 1) {
        if (newQty > 0) {
          appendStokisStockRow_(sheet, stokisId, productId, newQty);
        }
        invalidateAllCaches();
        return { success: true, message: "Stok berhasil diperbarui" };
      }

      const data = sheet.getRange(2, 1, lastRow - 1, 5).getValues();
      const rowsToDelete = [];
      let productName = productId;
      for (let i = 0; i < data.length; i++) {
        const rowStokisId = String(data[i][0]).trim();
        const rowProductId = String(data[i][1]).trim();
        if (rowStokisId === String(stokisId).trim() && rowProductId === String(productId).trim()) {
          rowsToDelete.push(i + 2); // +2 karena data mulai dari baris ke-2 (header di baris 1)
          if (data[i][2]) productName = data[i][2];
        }
      }

      // Hapus dari baris terbawah ke atas supaya nomor baris yang belum dihapus tidak bergeser
      for (let j = rowsToDelete.length - 1; j >= 0; j--) {
        sheet.deleteRow(rowsToDelete[j]);
      }

      if (newQty > 0) {
        appendStokisStockRow_(sheet, stokisId, productId, newQty, productName);
      }

      invalidateAllCaches();
      return { success: true, message: newQty > 0 ? "Stok berhasil diperbarui" : "Stok berhasil dihapus" };
    } catch (e) {
      return { success: false, message: e.toString() };
    }
  });
}

// Helper internal untuk updateStokisStockItem: menambahkan satu baris konsolidasi
// baru ke sheet StokisStock dengan nama produk yang tepat (dicari dari sheet
// Products bila tidak diberikan lewat parameter productName).
function appendStokisStockRow_(sheet, stokisId, productId, qty, productName) {
  let name = productName;
  if (!name) {
    const ms = new MasterService();
    const pSheet = ms.getSheet("Products");
    if (pSheet && pSheet.getLastRow() > 1) {
      const data = pSheet.getRange(2, 1, pSheet.getLastRow() - 1, 2).getValues();
      for (let i = 0; i < data.length; i++) {
        if (String(data[i][0]).trim() === String(productId).trim()) {
          name = data[i][1];
          break;
        }
      }
    }
  }
  sheet.appendRow([stokisId, productId, name || productId, qty, new Date().toISOString()]);
}

function injectToSales(salesName, payload) {
  return executeWithLock(() => {
    try {
      const sss = new BaseSpreadsheetService("STOCK_SALES");
      let sheet = sss.getSheet("StockAllocations");
      if (!sheet) sheet = sss.insertSheet("StockAllocations");
      
      if (sheet.getLastRow() === 0) {
        sheet.appendRow(["Sales", "ProductID", "Product", "Qty", "Date"]);
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
      
      invalidateAllCaches(); return { success: true, message: "Stok berhasil di-inject ke Sales" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function injectFromStokisToSales(stokisId, salesName, payload) {
  return executeWithLock(() => {
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
        saRows.push([
           salesName,
           item.productId,
           pMap[item.productId] || item.productId,
           item.qty,
           dateStr
        ]);
        stRows.push([
           stokisId,
           item.productId,
           pMap[item.productId] || item.productId,
           -item.qty,
           dateStr
        ]);
      });
      
      if (saRows.length > 0) {
        if (saSheet.getLastRow() === 0) saSheet.appendRow(["Sales", "ProductID", "Product", "Qty", "Date"]);
        saSheet.getRange(saSheet.getLastRow() + 1, 1, saRows.length, 5).setValues(saRows);
      }
      
      if (stRows.length > 0) {
        if (stSheet.getLastRow() === 0) stSheet.appendRow(["StokisID", "ProductID", "ProductName", "Qty", "Date"]);
        stSheet.getRange(stSheet.getLastRow() + 1, 1, stRows.length, 5).setValues(stRows);
      }
      
      invalidateAllCaches(); return { success: true, message: "Berhasil ditarik ke mobil Anda" };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

// PERBAIKAN LOGIC (alur "Inject Outlet ke Rute Sales" belum sesuai kebutuhan):
// sebelumnya fungsi ini HANYA menerima SATU nama outlet per panggilan, sehingga
// admin harus mengulang 3 langkah (pilih sales -> pilih hari -> pilih 1 outlet ->
// simpan) SATU PER SATU untuk setiap outlet -- padahal outlet yang masuk ke database
// (lewat NOO maupun upload massal) SUDAH punya kolom "KodeHari" sendiri. Sekarang
// fungsi ini menerima ARRAY nama outlet sekaligus (dikirim oleh frontend yang sudah
// otomatis menampilkan SEMUA outlet dengan KodeHari yang cocok saat admin memilih
// sales+hari), dan menuliskannya dalam SATU kali operasi tulis sheet (bukan
// appendRow berulang yang lambat), plus melewati (skip) outlet yang sudah pernah
// ditugaskan ke sales+hari yang sama persis supaya tidak dobel kalau tombol simpan
// tidak sengaja diklik ulang. Parameter outletNames tetap menerima string tunggal
// (bukan array) supaya kompatibel ke belakang bila ada pemanggil lama.
function injectOutletToSales(salesName, day, outletNames) {
  return executeWithLock(() => {
    try {
      const ms = new MasterService();
      let sheet = ms.getSheet("InjectOutlets");
      if (!sheet) {
        sheet = ms.insertSheet("InjectOutlets");
        sheet.appendRow(["Sales", "Day", "Outlet", "Date"]);
      }
      if (sheet.getLastRow() === 0) {
        sheet.appendRow(["Sales", "Day", "Outlet", "Date"]);
      }

      const namesArray = Array.isArray(outletNames) ? outletNames : [outletNames];
      const cleanNames = namesArray.map(n => String(n || "").trim()).filter(n => n.length > 0);
      if (cleanNames.length === 0) {
        return { success: false, message: "Tidak ada outlet yang dipilih." };
      }

      // Cek outlet yang sudah pernah ditugaskan ke sales+hari yang SAMA PERSIS,
      // supaya tidak membuat baris duplikat kalau tombol simpan diklik berkali-kali
      // atau sebagian outlet sudah pernah di-inject sebelumnya.
      const existing = {};
      const lastRow = sheet.getLastRow();
      if (lastRow > 1) {
        const existingData = sheet.getRange(2, 1, lastRow - 1, 3).getValues();
        existingData.forEach(r => {
          const key = String(r[0]).trim().toLowerCase() + "||" + String(r[1]).trim().toLowerCase() + "||" + String(r[2]).trim().toLowerCase();
          existing[key] = true;
        });
      }

      const dateStr = new Date().toISOString();
      const rowsToAppend = [];
      let skippedCount = 0;
      cleanNames.forEach(outletName => {
        const key = String(salesName).trim().toLowerCase() + "||" + String(day).trim().toLowerCase() + "||" + outletName.toLowerCase();
        if (existing[key]) {
          skippedCount++;
          return;
        }
        rowsToAppend.push([salesName, day, outletName, dateStr]);
      });

      if (rowsToAppend.length > 0) {
        sheet.getRange(sheet.getLastRow() + 1, 1, rowsToAppend.length, 4).setValues(rowsToAppend);
      }

      invalidateAllCaches();
      let message = `${rowsToAppend.length} outlet berhasil di-inject ke rute ${salesName} (${day}).`;
      if (skippedCount > 0) message += ` ${skippedCount} outlet dilewati karena sudah pernah ditugaskan sebelumnya.`;
      return { success: true, message: message, inserted: rowsToAppend.length, skipped: skippedCount };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

// =========================================================================
// GENERATOR KODE OUTLET (SATU SUMBER KEBENARAN)
// =========================================================================
// FITUR BARU: sebelumnya ada 3 cara berbeda menghasilkan ID outlet baru:
//  - saveOutlet()      (Tambah Outlet manual di web) -> "OUT-" + timestamp
//  - saveOutletNOO()   (submit outlet baru dari sales di HP)  -> angka urut "000001"
//  - bulkImportOutlets() (upload CSV)                -> "OUT-" + timestamp + index
// Formatnya TIDAK KONSISTEN, sehingga data dari 3 sumber ini tidak nyambung/rapi
// di satu tabel yang sama. Sekarang SEMUA sumber memakai fungsi generator yang
// sama persis di bawah ini: format angka urut 6 digit dengan leading zero
// (000001, 000002, dst), disimpan sebagai nilai kolom ID di sheet Outlets.
// Nomor urut dihitung dari nilai numerik TERTINGGI yang sudah ada di kolom ID
// (termasuk ID lama yang mungkin diinput manual seperti "2" atau "4"), sehingga
// tidak akan pernah tabrakan dengan data lama.
function _scanMaxOutletIdNum(sheet) {
  let maxId = 0;
  const lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    const ids = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
    for (let i = 0; i < ids.length; i++) {
      const numStr = String(ids[i][0]).replace(/[^0-9]/g, '');
      const num = parseInt(numStr, 10);
      if (!isNaN(num) && num > maxId) maxId = num;
    }
  }
  return maxId;
}

// Generate SATU kode outlet baru berikutnya, mis. "000005".
function generateNextOutletId(sheet) {
  const maxId = _scanMaxOutletIdNum(sheet);
  return String(maxId + 1).padStart(6, '0');
}

// Reservasi SEKALIGUS banyak kode outlet berurutan untuk kebutuhan import CSV
// massal (mis. 50 outlet baru -> 50 kode berurutan), tanpa perlu scan ulang
// sheet satu-satu per baris (supaya cepat & tidak menahan lock lama).
function reserveOutletIdBlock(sheet, count) {
  const maxId = _scanMaxOutletIdNum(sheet);
  const ids = [];
  for (let i = 1; i <= count; i++) {
    ids.push(String(maxId + i).padStart(6, '0'));
  }
  return ids;
}

function saveOutlet(id, name, address, type) {
  return executeWithLock(() => {
    try {
      const ms = new MasterService();
      let sheet = ms.getSheet("Outlets");
      if (!sheet) return { success: false, message: "Sheet Outlets tidak ada." };
      
      if (!id || id.trim() === '') id = generateNextOutletId(sheet);
      
      let lastRow = sheet.getLastRow();
      let isUpdate = false;
      if (lastRow > 1) {
        let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.getRange(i + 2, 2, 1, 3).setValues([[name, address, type]]);
            isUpdate = true;
            break;
          }
        }
      }
      
      if (!isUpdate) {
        sheet.appendRow([id, name, address, type, "RETAIL", "", "", "", "Senin"]);
      }
      
      invalidateAllCaches();
      return { success: true, message: "Outlet berhasil disimpan.", id: id };
    } catch(e) {
      return { success: false, message: e.toString() };
    }
  });
}

// FITUR BARU: Import CSV massal untuk Outlets, agar Admin bisa memakai database
// outlet yang sudah ada (dari sistem lama) tanpa input satu-satu secara manual.
// Kolom CSV di sini SENGAJA dibuat sama persis dengan kolom sheet Outlets
// (ID, Name, Address, Type, PriceTier, Geotag, SalesID, SalesName, KodeHari)
// supaya outlet hasil import CSV dan outlet hasil approval NOO dari sales
// terintegrasi rapi di tabel yang sama, tanpa perbedaan format.
// - Baris dengan ID yang cocok dengan data existing -> di-UPDATE.
// - Baris tanpa ID (atau ID yang tidak ditemukan) -> dianggap outlet BARU, dan
//   ID digenerate otomatis format angka urut 6 digit "000001", "000002", dst
//   (fungsi generator yang sama dengan Tambah Outlet manual & NOO sales),
//   direservasi sekaligus per-batch supaya tidak tabrakan & tetap cepat.
// - Baris tanpa Nama Toko dilewati (dilaporkan sebagai error/skip, tidak
//   menggagalkan keseluruhan proses import).
function bulkImportOutlets(rows) {
  return executeWithLock(() => {
    try {
      if (!rows || !Array.isArray(rows) || rows.length === 0) {
        return { success: false, message: "Tidak ada data untuk diimport." };
      }

      const ms = new MasterService();
      let sheet = ms.getSheet("Outlets");
      if (!sheet) {
        sheet = ms.insertSheet("Outlets");
        sheet.appendRow(["ID", "Name", "Address", "Type", "PriceTier", "Geotag", "SalesID", "SalesName", "KodeHari"]);
      }

      const lastRow = sheet.getLastRow();
      const existingData = lastRow > 1 ? sheet.getRange(2, 1, lastRow - 1, 9).getValues() : [];
      const idRowMap = {};
      existingData.forEach((row, idx) => {
        const key = String(row[0]).trim();
        if (key) idRowMap[key] = idx + 2; // +2: offset header row & 0-based index
      });

      // Tahap 1: klasifikasikan tiap baris CSV -> update (ID cocok) atau baru
      // (butuh ID auto-generate). Hitung dulu berapa banyak yang butuh ID baru,
      // supaya bisa reservasi satu blok kode sekaligus (efisien, tidak menahan
      // lock lama untuk import ratusan baris).
      const parsedRows = [];
      let needNewIdCount = 0;
      let skipped = 0;
      const errors = [];

      rows.forEach((r, idx) => {
        const rowNumForLog = idx + 2; // perkiraan nomor baris di CSV (baris 1 = header)
        const name = String(r.name || "").trim();
        if (!name) {
          skipped++;
          errors.push(`Baris ${rowNumForLog}: Nama Toko kosong, dilewati.`);
          return;
        }

        const id = String(r.id || "").trim();
        const address = String(r.address || "").trim();
        const type = String(r.type || "Regular").trim() || "Regular";
        const priceTier = String(r.priceTier || "RETAIL").trim() || "RETAIL";
        const geotag = String(r.geotag || "").trim();
        const salesId = String(r.salesId || "").trim();
        const salesName = String(r.salesName || "").trim();
        const kodeHari = String(r.kodeHari || "Senin").trim() || "Senin";

        const willUpdate = !!(id && idRowMap[id]);
        if (!willUpdate) needNewIdCount++;

        parsedRows.push({ id, name, address, type, priceTier, geotag, salesId, salesName, kodeHari, willUpdate });
      });

      const reservedIds = needNewIdCount > 0 ? reserveOutletIdBlock(sheet, needNewIdCount) : [];
      let reservedPointer = 0;

      let inserted = 0, updated = 0;
      const rowsToAppend = [];

      parsedRows.forEach((r) => {
        if (r.willUpdate) {
          const targetRow = idRowMap[r.id];
          sheet.getRange(targetRow, 2, 1, 8).setValues([[r.name, r.address, r.type, r.priceTier, r.geotag, r.salesId, r.salesName, r.kodeHari]]);
          updated++;
        } else {
          const newId = reservedIds[reservedPointer++];
          rowsToAppend.push([newId, r.name, r.address, r.type, r.priceTier, r.geotag, r.salesId, r.salesName, r.kodeHari]);
          inserted++;
        }
      });

      if (rowsToAppend.length > 0) {
        sheet.getRange(sheet.getLastRow() + 1, 1, rowsToAppend.length, 9).setValues(rowsToAppend);
      }

      invalidateAllCaches();
      return {
        success: true,
        inserted, updated, skipped, errors,
        message: `Import selesai: ${inserted} outlet baru ditambahkan, ${updated} diperbarui, ${skipped} dilewati.`
      };
    } catch(e) {
      return { success: false, message: e.toString() };
    }
  });
}

function deleteOutlet(id) {
  return executeWithLock(() => {
    try {
      const ms = new MasterService();
      let sheet = ms.getSheet("Outlets");
      if (!sheet) return { success: false, message: "Sheet Outlets tidak ada." };
      let lastRow = sheet.getLastRow();
      if (lastRow > 1) {
        let data = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(id).trim()) {
            sheet.deleteRow(i + 2);
            invalidateAllCaches();
            return { success: true, message: "Outlet berhasil dihapus." };
          }
        }
      }
      return { success: false, message: "Outlet tidak ditemukan." };
    } catch(e) {
      return { success: false, message: e.toString() };
    }
  });
}

function saveTransactionAdmin(orderId, date, outletName, total, paymentMethod) {
  return executeWithLock(() => {
    try {
      const isv = new InvoiceService();
      let ym = DateUtil.getYearMonth(date);
      let sheetName = "Transactions_" + ym;
      let sheet = isv.getSheet(sheetName);
      if (!sheet) {
        const ss = isv.getSpreadsheet();
        const sheets = ss.getSheets();
        for (let s of sheets) {
          if (s.getName().startsWith("Transactions_")) {
            let lastRow = s.getLastRow();
            if (lastRow > 1) {
              let data = s.getRange(2, 1, lastRow - 1, 1).getValues();
              for (let i = 0; i < data.length; i++) {
                if (String(data[i][0]).trim() === String(orderId).trim()) {
                  sheet = s;
                  break;
                }
              }
            }
          }
          if (sheet) break;
        }
      }
      
      if (!sheet) return { success: false, message: "Sheet transaksi tidak ditemukan." };
      
      let lastRow = sheet.getLastRow();
      if (lastRow > 1) {
        let headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
        let colOrderId = headers.indexOf("OrderId") + 1;
        let colDate = headers.indexOf("Date") + 1;
        let colOutletName = headers.indexOf("OutletName") + 1;
        let colTotal = headers.indexOf("Total") + 1;
        let colPaymentMethod = headers.indexOf("PaymentMethod") + 1;
        
        let data = sheet.getRange(2, colOrderId, lastRow - 1, 1).getValues();
        for (let i = 0; i < data.length; i++) {
          if (String(data[i][0]).trim() === String(orderId).trim()) {
            let rowNum = i + 2;
            if (colDate > 0) sheet.getRange(rowNum, colDate).setValue(date);
            if (colOutletName > 0) sheet.getRange(rowNum, colOutletName).setValue(outletName);
            if (colTotal > 0) sheet.getRange(rowNum, colTotal).setValue(Number(total));
            if (colPaymentMethod > 0) sheet.getRange(rowNum, colPaymentMethod).setValue(paymentMethod);
            
            invalidateAllCaches();
            return { success: true, message: "Transaksi berhasil diperbarui." };
          }
        }
      }
      return { success: false, message: "Transaksi dengan Order ID " + orderId + " tidak ditemukan." };
    } catch (e) {
      return { success: false, message: e.toString() };
    }
  });
}

function saveOutletNOO(name, address, geo, day, salesName) {
  return executeWithLock(() => {
    try {
      const ms = new MasterService();
      let sheet = ms.getSheet("Outlets");
      if (!sheet) {
        sheet = ms.insertSheet("Outlets");
        sheet.appendRow(["ID", "Name", "Address", "Type", "PriceTier", "Geotag", "SalesID", "SalesName", "KodeHari"]);
      }
      
      // PERBAIKAN: sebelumnya logika scan ID tertinggi diduplikasi di sini secara
      // terpisah dari saveOutlet()/bulkImportOutlets(). Sekarang pakai satu fungsi
      // generator yang sama (generateNextOutletId) supaya format & urutan ID selalu
      // konsisten dari sumber manapun outlet baru itu berasal.
      let newId = generateNextOutletId(sheet);
      let today = new Date();
      let dd = String(today.getDate()).padStart(2, '0');
      let mm = String(today.getMonth() + 1).padStart(2, '0');
      let keterangan = "NOO " + dd + "/" + mm;
      sheet.appendRow([newId, name, address, keterangan, "Retail", geo, "", salesName, day]);
      
      // Also inject to today's route
      injectOutletToSales(salesName, day, name);
      invalidateAllCaches();
      return { success: true, message: "Outlet berhasil didaftarkan dengan ID: " + newId };
    } catch(e) { return { success: false, message: e.toString() }; }
  });
}

function executeRealArchive() {
  return executeWithLock(() => {
    try {
      ArchiveService.archiveOldTransactions(false);
      invalidateAllCaches(); return { success: true, message: "Proses Archive berhasil diselesaikan." };
    } catch (err) {
      return { success: false, message: err.toString() };
    }
  });
}

// Ensure the new wrappers are at the end.

function onEdit(e) {
  try {
    invalidateAllCaches();
  } catch(err) {}
}

function invalidateAllCaches() {
  try {
    const cache = CacheService.getScriptCache();
    cache.remove("getUsers");
    cache.remove("getProducts");
    cache.remove("getOutlets");
    const version = PropertiesService.getScriptProperties().getProperty("MASTER_DATA_VERSION") || "v1";
    cache.remove("getInitialData_" + version);
    cache.remove("getInitialData_" + version + "_");
    PropertiesService.getScriptProperties().setProperty("MASTER_DATA_VERSION", "v" + new Date().getTime());
  } catch(e) {}
}

function createDatabase() {
  try {
    const ms = new MasterService();
    let logs = [];
    
    // 1. Setup Users
    let usersSheet = ms.getSheet("Users");
    if (!usersSheet) {
      usersSheet = ms.insertSheet("Users");
      usersSheet.appendRow(["ID", "Username", "Password", "Role"]);
      usersSheet.appendRow(["ADM-01", "admin", "admin123", "Admin"]);
      usersSheet.appendRow(["SLS-01", "WAWAN", "sales123", "Sales"]);
      logs.push("Sheet 'Users' created with default Admin and Salesman");
    } else {
      logs.push("Sheet 'Users' already exists");
    }
    
    // 2. Setup Products
    let productsSheet = ms.getSheet("Products");
    if (!productsSheet) {
      productsSheet = ms.insertSheet("Products");
      productsSheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);
      productsSheet.appendRow(["PRD-01", "Produk Contoh 1", 10000, 8000, 100]);
      productsSheet.appendRow(["PRD-02", "Produk Contoh 2", 15000, 12000, 200]);
      logs.push("Sheet 'Products' created with default products");
    } else {
      logs.push("Sheet 'Products' already exists");
    }
    
    // 3. Setup Setting
    let settingSheet = ms.getSheet("Setting");
    if (!settingSheet) {
      settingSheet = ms.insertSheet("Setting");
      settingSheet.appendRow(["Key", "Value"]);
      settingSheet.appendRow(["ARCHIVE_KEEP_MONTHS", "3"]);
      settingSheet.appendRow(["CHUNK_SIZE", "5000"]);
      settingSheet.appendRow(["LOCK_TIMEOUT", "30000"]);
      logs.push("Sheet 'Setting' created with default configuration");
    } else {
      logs.push("Sheet 'Setting' already exists");
    }
    
    // 4. Setup Outlets
    let outletsSheet = ms.getSheet("Outlets");
    if (!outletsSheet) {
      outletsSheet = ms.insertSheet("Outlets");
      outletsSheet.appendRow(["ID", "Name", "Address", "Type", "PriceTier", "Geotag", "SalesID", "SalesName", "KodeHari"]);
      outletsSheet.appendRow(["000001", "Outlet Contoh 1", "Jl. Merdeka No. 1", "Regular", "Retail", "0,0", "SLS-01", "WAWAN", "Senin"]);
      logs.push("Sheet 'Outlets' created with a default outlet");
    } else {
      logs.push("Sheet 'Outlets' already exists");
    }
    
    // 5. Setup InjectOutlets
    let injectOutletsSheet = ms.getSheet("InjectOutlets");
    if (!injectOutletsSheet) {
      injectOutletsSheet = ms.insertSheet("InjectOutlets");
      injectOutletsSheet.appendRow(["Sales", "Day", "Outlet", "Date"]);
      logs.push("Sheet 'InjectOutlets' created");
    } else {
      logs.push("Sheet 'InjectOutlets' already exists");
    }

    // 6. Setup Stokis
    let stokisSheet = ms.getSheet("Stokis");
    if (!stokisSheet) {
      stokisSheet = ms.insertSheet("Stokis");
      stokisSheet.appendRow(["ID", "Name", "Address", "SalesAuthority"]);
      stokisSheet.appendRow(["STK-01", "Stokis Utama", "Gudang Cabang 1", "WAWAN"]);
      logs.push("Sheet 'Stokis' created with a default stokis");
    } else {
      logs.push("Sheet 'Stokis' already exists");
    }

    // 7. Setup StokisStock & StockAllocations
    const sss = new BaseSpreadsheetService("STOCK_SALES");
    let stokisStockSheet = sss.getSheet("StokisStock");
    if (!stokisStockSheet) {
      stokisStockSheet = sss.insertSheet("StokisStock");
      stokisStockSheet.appendRow(["StokisID", "ProductID", "ProductName", "Qty", "Date"]);
      logs.push("Sheet 'StokisStock' created");
    } else {
      logs.push("Sheet 'StokisStock' already exists");
    }

    let stockAllocationsSheet = sss.getSheet("StockAllocations");
    if (!stockAllocationsSheet) {
      stockAllocationsSheet = sss.insertSheet("StockAllocations");
      stockAllocationsSheet.appendRow(["Sales", "ProductID", "Product", "Qty", "Date"]);
      logs.push("Sheet 'StockAllocations' created");
    } else {
      logs.push("Sheet 'StockAllocations' already exists");
    }

    // 8. Setup RouteAssignments
    const rass = new BaseSpreadsheetService("ROUTE_ASSIGNMENTS");
    let raSheet = rass.getSheet("RouteAssignments");
    if (!raSheet) {
      raSheet = rass.insertSheet("RouteAssignments");
      raSheet.appendRow(["Sales", "Day", "OutletName"]);
      raSheet.appendRow(["WAWAN", "Senin", "Outlet Contoh 1"]);
      logs.push("Sheet 'RouteAssignments' created");
    } else {
      logs.push("Sheet 'RouteAssignments' already exists");
    }

    // 9. Setup Transactions (Current Month)
    const trxService = new BaseSpreadsheetService("TRANSACTIONS");
    let ym = DateUtil.getYearMonth(new Date().toISOString());
    let currentTrxSheetName = "Transactions_" + ym;
    let trxSheet = trxService.getSheet(currentTrxSheetName);
    if (!trxSheet) {
      trxSheet = trxService.insertSheet(currentTrxSheetName);
      trxSheet.appendRow(["OrderId", "Date", "SalesId", "SalesName", "OutletId", "OutletName", "OutletType", "Geotag", "Total", "PaymentMethod", "Items", "UploadedAt"]);
      logs.push("Sheet '" + currentTrxSheetName + "' created");
    } else {
      logs.push("Sheet '" + currentTrxSheetName + "' already exists");
    }

    invalidateAllCaches();
    return { success: true, message: "Database initialized successfully.\n" + logs.join("\n") };
  } catch (e) {
    return { success: false, message: "Failed to initialize database: " + e.toString() };
  }
}
