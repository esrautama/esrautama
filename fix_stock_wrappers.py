def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    # StokisStock Headers
    old_stokis_header = """sheet.appendRow(["StokisId", "ProductId", "Qty", "Date"]);"""
    new_stokis_header = """sheet.appendRow(["StokisID", "ProductID", "ProductName", "Qty", "Date"]);"""
    
    # StockAllocations Headers
    old_sales_header = """sheet.appendRow(["Sales", "ProductId", "ProductName", "Qty", "Date"]);"""
    new_sales_header = """sheet.appendRow(["Sales", "ProductID", "ProductName", "Qty", "Date"]);"""
    
    # injectFromStokisToSales Headers
    old_stokis_header2 = """stSheet.appendRow(["StokisId", "ProductId", "ProductName", "Qty", "Date"]);"""
    new_stokis_header2 = """stSheet.appendRow(["StokisID", "ProductID", "ProductName", "Qty", "Date"]);"""
    
    if old_stokis_header in content:
        content = content.replace(old_stokis_header, new_stokis_header)
    if old_sales_header in content:
        content = content.replace(old_sales_header, new_sales_header)
    if old_stokis_header2 in content:
        content = content.replace(old_stokis_header2, new_stokis_header2)
        
    with open(file_name, "w") as f:
        f.write(content)
    print(f"Patched {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
