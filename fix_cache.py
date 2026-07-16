import re

with open("AppsScript_Full.gs", "r") as f:
    text = f.read()

# Fix 1: Make getInitialData respect MASTER_DATA_VERSION
target1 = """const version = clientVersion || "v1.0.0";
    const cacheKey = "getInitialData_" + version + "_" + (lastSyncStr ? lastSyncStr.substring(0,10) : "all");"""
replacement1 = """const version = clientVersion || "v1.0.0";
    const masterVersion = PropertiesService.getScriptProperties().getProperty("MASTER_DATA_VERSION") || "v1";
    const cacheKey = "getInitialData_" + masterVersion + "_" + version + "_" + (lastSyncStr ? lastSyncStr.substring(0,10) : "all");"""

if target1 in text:
    text = text.replace(target1, replacement1)
    print("Patched target1")
else:
    print("Could not find target1")

# Fix 2: saveProduct header names
target2 = """sheet.appendRow(["ID", "Name", "Retail", "Wholesale", "Stock"]);"""
replacement2 = """sheet.appendRow(["ID", "Name", "PriceRetail", "PriceWholesale", "WarehouseStock", "StockistStock"]);"""
if target2 in text:
    text = text.replace(target2, replacement2)
    print("Patched target2")
else:
    print("Could not find target2")

# Fix 3: saveProduct append row
target3 = """if (!isUpdate) {
      sheet.appendRow([id, name, retail, wholesale, stock]);
    }"""
replacement3 = """if (!isUpdate) {
      sheet.appendRow([id, name, retail, wholesale, stock, 0]);
    }"""
if target3 in text:
    text = text.replace(target3, replacement3)
    print("Patched target3")
else:
    print("Could not find target3")

with open("AppsScript_Full.gs", "w") as f:
    f.write(text)

