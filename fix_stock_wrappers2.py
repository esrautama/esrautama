def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    # StockAllocations Headers
    old_sales_header = """sheet.appendRow(["Sales", "ProductID", "ProductName", "Qty", "Date"]);"""
    new_sales_header = """sheet.appendRow(["Sales", "ProductID", "Product", "Qty", "Date"]);"""
    
    # injectFromStokisToSales Headers
    old_stokis_header2 = """saSheet.appendRow(["Sales", "ProductId", "ProductName", "Qty", "Date"]);"""
    new_stokis_header2 = """saSheet.appendRow(["Sales", "ProductID", "Product", "Qty", "Date"]);"""
    
    # Also check if it was already Sales, ProductID, ProductName
    old_stokis_header3 = """saSheet.appendRow(["Sales", "ProductID", "ProductName", "Qty", "Date"]);"""
    new_stokis_header3 = """saSheet.appendRow(["Sales", "ProductID", "Product", "Qty", "Date"]);"""
    
    if old_sales_header in content:
        content = content.replace(old_sales_header, new_sales_header)
    if old_stokis_header2 in content:
        content = content.replace(old_stokis_header2, new_stokis_header2)
    if old_stokis_header3 in content:
        content = content.replace(old_stokis_header3, new_stokis_header3)
        
    with open(file_name, "w") as f:
        f.write(content)
    print(f"Patched {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
