def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    new_code = r"""        let key = headers[j] ? String(headers[j]).trim() : "Col" + j;
        let cleanKey = key.replace(/\s+/g, '').toLowerCase();
        obj[key] = row[j];
        // add case variants to make it bulletproof
        if (cleanKey === 'stokisid') { obj['StokisID'] = row[j]; obj['stokisId'] = row[j]; }
        if (cleanKey === 'productid') { obj['ProductID'] = row[j]; obj['productId'] = row[j]; obj['Product'] = row[j]; obj['product'] = row[j]; }
        if (cleanKey === 'productname' || cleanKey === 'product') { obj['Product'] = row[j]; obj['product'] = row[j]; obj['ProductName'] = row[j]; }
        if (cleanKey === 'qty') { obj['Qty'] = row[j]; obj['qty'] = row[j]; }
        if (cleanKey === 'date') { obj['Date'] = row[j]; obj['date'] = row[j]; }
        if (cleanKey === 'sales' || cleanKey === 'salesauthority') { obj['Sales'] = row[j]; obj['sales'] = row[j]; obj['SalesAuthority'] = row[j]; obj['salesAuthority'] = row[j]; }
        if (cleanKey === 'username' || cleanKey === 'name') { obj['Username'] = row[j]; obj['username'] = row[j]; obj['Name'] = row[j]; obj['name'] = row[j]; }
        if (cleanKey === 'password') { obj['Password'] = row[j]; obj['password'] = row[j]; }
        if (cleanKey === 'role') { obj['Role'] = row[j]; obj['role'] = row[j]; }
        if (cleanKey === 'id') { obj['ID'] = row[j]; obj['id'] = row[j]; }
        if (cleanKey === 'address') { obj['Address'] = row[j]; obj['address'] = row[j]; }
        if (cleanKey === 'retail' || cleanKey === 'priceretail') { obj['Retail'] = row[j]; obj['retail'] = row[j]; }
        if (cleanKey === 'wholesale' || cleanKey === 'pricewholesale') { obj['Wholesale'] = row[j]; obj['wholesale'] = row[j]; }
        if (cleanKey === 'stock' || cleanKey === 'warehousestock') { obj['Stock'] = row[j]; obj['stock'] = row[j]; }"""

    import re
    pattern = re.compile(r'let key = headers\[j\] \? String\(headers\[j\]\)\.trim\(\) : "Col" \+ j;[\s\S]*?obj\[\'role\'\] = row\[j\]; }')
    
    if pattern.search(content):
        content = pattern.sub(new_code.replace('\\', '\\\\'), content)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
