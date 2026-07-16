def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """      const obj = {};
      for (let j = 0; j < headers.length; j++) {
        obj[headers[j]] = row[j];
      }
      result.push(obj);"""
      
    new_code = """      const obj = {};
      for (let j = 0; j < headers.length; j++) {
        let key = headers[j] ? String(headers[j]).trim() : "Col" + j;
        obj[key] = row[j];
        // add case variants to make it bulletproof
        if (key.toLowerCase() === 'stokisid') { obj['StokisID'] = row[j]; obj['stokisId'] = row[j]; }
        if (key.toLowerCase() === 'productid') { obj['ProductID'] = row[j]; obj['productId'] = row[j]; obj['Product'] = row[j]; obj['product'] = row[j]; }
        if (key.toLowerCase() === 'productname' || key.toLowerCase() === 'product') { obj['Product'] = row[j]; obj['product'] = row[j]; obj['ProductName'] = row[j]; }
        if (key.toLowerCase() === 'qty') { obj['Qty'] = row[j]; obj['qty'] = row[j]; }
        if (key.toLowerCase() === 'date') { obj['Date'] = row[j]; obj['date'] = row[j]; }
        if (key.toLowerCase() === 'sales') { obj['Sales'] = row[j]; obj['sales'] = row[j]; }
        if (key.toLowerCase() === 'username') { obj['Username'] = row[j]; obj['username'] = row[j]; }
        if (key.toLowerCase() === 'password') { obj['Password'] = row[j]; obj['password'] = row[j]; }
        if (key.toLowerCase() === 'role') { obj['Role'] = row[j]; obj['role'] = row[j]; }
      }
      result.push(obj);"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
