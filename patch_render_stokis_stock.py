def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """            const listStok = document.getElementById('listStokStokis');
            if (state.stokisStock && state.stokisStock.length > 0) {
                let grouped = {};
                state.stokisStock.forEach(st => {
                    let sId = st.StokisID || st.stokisId;
                    if(!grouped[sId]) grouped[sId] = [];
                    grouped[sId].push(st);
                });

                let html = '<div class="space-y-3">';
                var todayDate = new Date().toLocaleDateString('id-ID', {day: '2-digit', month: 'short', year: 'numeric'});

                for(let sId in grouped) {
                    let stokisName = state.stokisMaster.find(sm => (sm.ID || sm.id) === sId)?.Name || sId;
                    html += `
                    <div class="border border-gray-200 rounded-xl bg-white shadow-sm overflow-hidden">
                        <div class="p-4 bg-gray-50 flex justify-between items-center cursor-pointer hover:bg-gray-100 transition" onclick="toggleCollapse('col-stk-${sId}', 'icon-stk-${sId}')">
                            <div>
                                <h4 class="font-bold text-gray-800 text-sm">${stokisName}</h4>
                                <span class="text-[9px] text-gray-500">Update Terakhir: ${todayDate}</span>
                            </div>
                            <i id="icon-stk-${sId}" class="fas fa-chevron-down text-gray-400 transition-transform"></i>
                        </div>
                        <div id="col-stk-${sId}" class="hidden p-4 space-y-2 border-t border-gray-200 collapse-content">
                    `;
                    grouped[sId].forEach(st => {
                        let prodName = state.adminProducts.find(p => (p.ID || p.id) === (st.ProductID || st.product))?.Name || (st.ProductID || st.product);
                        html += `
                            <div class="flex justify-between items-center border-b border-gray-100 last:border-0 pb-2 last:pb-0">
                                <span class="font-bold text-gray-700 text-xs">${prodName}</span>
                                <span class="font-black text-blue-600 bg-blue-50 px-2 py-1 rounded text-xs">${st.Qty || st.qty} PCS</span>
                            </div>
                        `;
                    });
                    html += `</div></div>`;
                }
                html += '</div>';
                listStok.innerHTML = html;
            } else {
                listStok.innerHTML = '<span class="italic text-gray-400">Belum ada stok di Stokis. Silakan inject dari gudang di bawah ini.</span>';
            }"""
            
    new_code = """            const listStok = document.getElementById('listStokStokis');
            if (state.stokisStock && state.stokisStock.length > 0) {
                let grouped = {}; // grouped by stokisId
                state.stokisStock.forEach(st => {
                    let sId = st.StokisID || st.stokisId;
                    let pId = st.ProductID || st.product;
                    let qty = parseInt(st.Qty || st.qty) || 0;
                    if(!grouped[sId]) grouped[sId] = {};
                    if(!grouped[sId][pId]) grouped[sId][pId] = 0;
                    grouped[sId][pId] += qty;
                });

                let html = '<div class="space-y-3">';
                var todayDate = new Date().toLocaleDateString('id-ID', {day: '2-digit', month: 'short', year: 'numeric'});

                for(let sId in grouped) {
                    let stokisName = state.stokisMaster.find(sm => (sm.ID || sm.id) === sId)?.Name || sId;
                    html += `
                    <div class="border border-gray-200 rounded-xl bg-white shadow-sm overflow-hidden">
                        <div class="p-4 bg-gray-50 flex justify-between items-center cursor-pointer hover:bg-gray-100 transition" onclick="toggleCollapse('col-stk-${sId}', 'icon-stk-${sId}')">
                            <div>
                                <h4 class="font-bold text-gray-800 text-sm">${stokisName}</h4>
                                <span class="text-[9px] text-gray-500">Update Terakhir: ${todayDate}</span>
                            </div>
                            <i id="icon-stk-${sId}" class="fas fa-chevron-down text-gray-400 transition-transform"></i>
                        </div>
                        <div id="col-stk-${sId}" class="hidden p-4 space-y-2 border-t border-gray-200 collapse-content">
                    `;
                    
                    let hasStock = false;
                    for (let pId in grouped[sId]) {
                        let qty = grouped[sId][pId];
                        if (qty === 0) continue; // skip empty stock
                        hasStock = true;
                        let prodName = state.adminProducts.find(p => (p.ID || p.id) === pId)?.Name || pId;
                        html += `
                            <div class="flex justify-between items-center border-b border-gray-100 last:border-0 pb-2 last:pb-0">
                                <span class="font-bold text-gray-700 text-xs">${prodName}</span>
                                <span class="font-black text-blue-600 bg-blue-50 px-2 py-1 rounded text-xs">${qty} PCS</span>
                            </div>
                        `;
                    }
                    if (!hasStock) {
                        html += `<span class="italic text-gray-400 text-xs">Stok kosong</span>`;
                    }
                    
                    html += `</div></div>`;
                }
                html += '</div>';
                listStok.innerHTML = html;
            } else {
                listStok.innerHTML = '<span class="italic text-gray-400">Belum ada stok di Stokis. Silakan inject dari gudang di bawah ini.</span>';
            }"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("index_readable.html")
