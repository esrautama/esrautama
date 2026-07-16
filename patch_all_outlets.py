import re

# 1. AppsScript_Full.gs patch
with open("AppsScript_Full.gs", "r") as f:
    as_text = f.read()

new_as_functions = """
function saveOutlet(id, name, address, type) {
  try {
    const ms = new MasterService();
    let sheet = ms.getSheet("Outlets");
    if (!sheet) return { success: false, message: "Sheet Outlets tidak ada." };
    
    if (!id || id.trim() === '') id = "OUT-" + new Date().getTime();
    
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
    return { success: true, message: "Outlet berhasil disimpan." };
  } catch(e) {
    return { success: false, message: e.toString() };
  }
}

function deleteOutlet(id) {
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
}
"""

if "function saveOutlet(" not in as_text:
    as_text = as_text + "\n" + new_as_functions
    with open("AppsScript_Full.gs", "w") as f:
        f.write(as_text)
    print("Patched AppsScript_Full.gs")

# 2. Index.html patch - adding JS functions
with open("Index.html", "r") as f:
    html_text = f.read()

js_funcs = """
        function openFormOutlet(id='', name='', address='', type='Retail') {
            const isEdit = id !== '';
            const title = isEdit ? 'Edit Outlet' : 'Tambah Outlet Baru';
            const html = `
                <div class="space-y-5 mt-4">
                    <input type="hidden" id="outletId" value="${id}">
                    <div class="relative border border-gray-400 rounded-md px-3 py-2.5">
                        <label class="absolute -top-2.5 left-2 bg-white px-1 text-[10px] text-gray-500 font-bold">NAMA OUTLET</label>
                        <input type="text" id="outletName" value="${name}" class="w-full text-sm font-bold text-gray-800 focus:outline-none bg-transparent">
                    </div>
                    <div class="relative border border-gray-400 rounded-md px-3 py-2.5">
                        <label class="absolute -top-2.5 left-2 bg-white px-1 text-[10px] text-gray-500 font-bold">ALAMAT</label>
                        <input type="text" id="outletAddress" value="${address}" class="w-full text-sm font-bold text-gray-800 focus:outline-none bg-transparent">
                    </div>
                    <div class="relative border border-gray-400 rounded-md px-3 py-2.5">
                        <label class="absolute -top-2.5 left-2 bg-white px-1 text-[10px] text-gray-500 font-bold">TIPE</label>
                        <select id="outletType" class="w-full text-sm font-bold text-gray-800 focus:outline-none bg-transparent">
                            <option value="Retail" ${type==='Retail'?'selected':''}>Retail</option>
                            <option value="Grosir" ${type==='Grosir'?'selected':''}>Grosir</option>
                            <option value="Modern" ${type==='Modern'?'selected':''}>Modern</option>
                        </select>
                    </div>
                </div>
            `;
            openFormModal(title, html, saveOutletDb);
        }

        function saveOutletDb() {
            var id = document.getElementById('outletId').value;
            var name = document.getElementById('outletName').value.trim();
            var address = document.getElementById('outletAddress').value.trim();
            var type = document.getElementById('outletType').value;
            
            if(!name) return showMessage('Peringatan', 'Nama Outlet tidak boleh kosong.');
            
            closeFormModal(); showLoader(true, "Menyimpan Data Outlet...");
            google.script.run.withSuccessHandler(res => {
                if(res.success) processDataLoad(); else showMessage("Gagal", res.message);
            }).saveOutlet(id, name, address, type);
        }

        function deleteOutletDb(id, name) {
            if(!confirm("Yakin ingin menghapus Outlet: " + name + "?")) return;
            showLoader(true, "Menghapus Data Outlet...");
            google.script.run.withSuccessHandler(res => {
                if(res.success) processDataLoad(); else showMessage("Gagal", res.message);
            }).deleteOutlet(id);
        }
"""

if "function openFormOutlet(" not in html_text:
    idx = html_text.find("function openFormUser")
    if idx != -1:
        html_text = html_text[:idx] + js_funcs + "\n" + html_text[idx:]
        print("Patched JS functions in Index.html")


# 3. Index.html patch - adding Tambah Outlet button
target_add_btn = """<h3 class="font-black text-gray-800 text-xl">Outlets Master</h3>
                <p class="text-xs text-gray-400 italic">Data Master</p>
              </div>"""
replacement_add_btn = """<h3 class="font-black text-gray-800 text-xl">Outlets Master</h3>
                <p class="text-xs text-gray-400 italic">Data Master</p>
              </div>
              <button onclick="openFormOutlet()" class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded-lg shadow text-sm transition mb-4">
                  <i class="fas fa-plus mr-2"></i> Tambah Outlet
              </button>"""

if target_add_btn in html_text:
    html_text = html_text.replace(target_add_btn, replacement_add_btn)
    print("Patched Tambah Outlet button")

# 4. Index.html patch - adding Edit/Delete buttons to table
# Before: renderTable('tableBodyOutlets', state.outlets, o => `<tr><td class="font-bold text-xs">${o.Name||o.name}</td><td class="text-xs">${o.Address||o.address}</td><td class="text-xs">${o.Type||o.type}</td></tr>`);
target_render_outlet = """renderTable('tableBodyOutlets', state.outlets, o => `<tr><td class="font-bold text-xs">${o.Name||o.name}</td><td class="text-xs">${o.Address||o.address}</td><td class="text-xs">${o.Type||o.type}</td></tr>`);"""
replacement_render_outlet = """renderTable('tableBodyOutlets', state.outlets, o => `<tr>
<td class="font-bold text-xs">${o.Name||o.name}</td>
<td class="text-xs">${o.Address||o.address}</td>
<td class="text-xs">${o.Type||o.type}</td>
<td class="text-center">
    <button onclick="openFormOutlet('${o.ID||o.id}', '${o.Name||o.name}', '${o.Address||o.address}', '${o.Type||o.type}')" class="text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 w-8 h-8 rounded-lg transition mr-1" title="Edit"><i class="fas fa-edit"></i></button>
    <button onclick="deleteOutletDb('${o.ID||o.id}', '${o.Name||o.name}')" class="text-red-500 hover:text-red-700 bg-red-50 hover:bg-red-100 w-8 h-8 rounded-lg transition" title="Hapus"><i class="fas fa-trash"></i></button>
</td>
</tr>`);"""

if target_render_outlet in html_text:
    html_text = html_text.replace(target_render_outlet, replacement_render_outlet)
    print("Patched renderTable for Outlets")

# We also need to add empty <th> for Action in Outlets table
target_th = """<th>Tipe</th>
                    </tr>
                  </thead>
                  <tbody id="tableBodyOutlets"></tbody>"""
replacement_th = """<th>Tipe</th>
                      <th class="text-center w-24">Aksi</th>
                    </tr>
                  </thead>
                  <tbody id="tableBodyOutlets"></tbody>"""
if target_th in html_text:
    html_text = html_text.replace(target_th, replacement_th)
    print("Patched Outlets table header")

with open("Index.html", "w") as f:
    f.write(html_text)

