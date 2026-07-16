def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """        if (cleanKey === 'retail' || cleanKey === 'priceretail') { obj['Retail'] = row[j]; obj['retail'] = row[j]; }
        if (cleanKey === 'wholesale' || cleanKey === 'pricewholesale') { obj['Wholesale'] = row[j]; obj['wholesale'] = row[j]; }"""
    new_code = """        if (cleanKey === 'retail' || cleanKey === 'priceretail') { obj['Retail'] = row[j]; obj['retail'] = row[j]; obj['PriceRetail'] = row[j]; obj['priceRetail'] = row[j]; }
        if (cleanKey === 'wholesale' || cleanKey === 'pricewholesale') { obj['Wholesale'] = row[j]; obj['wholesale'] = row[j]; obj['PriceWholesale'] = row[j]; obj['priceWholesale'] = row[j]; }"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
