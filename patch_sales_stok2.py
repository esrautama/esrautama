with open("index_readable.html", "r") as f:
    text = f.read()

old_block = """                var sStock = state.stokisStock.filter(st => String(st.StokisID || st.stokisId) === String(myStokis.ID || myStokis.id));\\
                \\
                if (sStock.length === 0) {\\
                    pList.innerHTML = '<p class="text-xs italic text-gray-400">Gudang stokis Anda kosong.</p>';\\
                } else {\\
                    sStock.forEach(st => {\\
                        var prodName = state.adminProducts.find(p => (p.ID || p.id) === (st.ProductID || st.product))?.Name || (st.ProductID || st.product);\\
                        var prodId = st.ProductID || st.product;\\
                        pList.innerHTML += `\\
                        <div class="flex justify-between items-center p-3 border border-gray-100 rounded-lg hover:bg-gray-50 transition-colors bg-white">\\
                            <div class="flex-1 pr-2">\\
                                <h4 class="font-bold text-xs text-gray-800 uppercase leading-tight">${prodName}</h4>\\
                                <p class="text-[10px] text-gray-400 font-medium mt-1">Stok Tersedia: ${st.Qty || st.qty || 0} PCS</p>\\
                            </div>\\
                            <div class="w-16 flex-shrink-0">\\
                                <input type="number" min="0" max="${st.Qty || st.qty}" placeholder="0" data-id="${prodId}" class="self-qty-input w-full text-xs font-black text-center text-blue-700 bg-gray-50 border border-gray-200 rounded px-1 py-1.5 focus:outline-none focus:border-blue-500 shadow-inner" oninput="updateSelfInjectCount(this)">\\
                            </div>\\
                        </div>`;\\
                    });\\
                }\\"""

new_block = """                var sStock = state.stokisStock.filter(st => String(st.StokisID || st.stokisId) === String(myStokis.ID || myStokis.id));\\
                var aggregatedStock = {};\\
                sStock.forEach(st => {\\
                    var pId = st.ProductID || st.product;\\
                    if (!aggregatedStock[pId]) aggregatedStock[pId] = 0;\\
                    aggregatedStock[pId] += parseInt(st.Qty || st.qty) || 0;\\
                });\\
                var hasStock = false;\\
                for (var k in aggregatedStock) { if(aggregatedStock[k] > 0) hasStock = true; }\\
                \\
                if (!hasStock) {\\
                    pList.innerHTML = '<p class="text-xs italic text-gray-400">Gudang stokis Anda kosong.</p>';\\
                } else {\\
                    for (var prodId in aggregatedStock) {\\
                        var qty = aggregatedStock[prodId];\\
                        if (qty <= 0) continue;\\
                        var prodName = state.adminProducts.find(p => (p.ID || p.id) === prodId)?.Name || prodId;\\
                        pList.innerHTML += `\\
                        <div class="flex justify-between items-center p-3 border border-gray-100 rounded-lg hover:bg-gray-50 transition-colors bg-white">\\
                            <div class="flex-1 pr-2">\\
                                <h4 class="font-bold text-xs text-gray-800 uppercase leading-tight">${prodName}</h4>\\
                                <p class="text-[10px] text-gray-400 font-medium mt-1">Stok Tersedia: ${qty} PCS</p>\\
                            </div>\\
                            <div class="w-16 flex-shrink-0">\\
                                <input type="number" min="0" max="${qty}" placeholder="0" data-id="${prodId}" class="self-qty-input w-full text-xs font-black text-center text-blue-700 bg-gray-50 border border-gray-200 rounded px-1 py-1.5 focus:outline-none focus:border-blue-500 shadow-inner" oninput="updateSelfInjectCount(this)">\\
                            </div>\\
                        </div>`;\\
                    }\\
                }\\"""

if old_block in text:
    text = text.replace(old_block, new_block)
    with open("index_readable.html", "w") as f:
        f.write(text)
    print("Replaced self inject")
else:
    print("Not found")

