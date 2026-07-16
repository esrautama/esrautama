with open("index_readable.html", "r") as f:
    text = f.read()

import re

start_idx = text.find("var sStock = state.stokisStock.filter(st => String(st.StokisID || st.stokisId) === String(myStokis.ID || myStokis.id));")
end_idx = text.find("var btnAction = document.getElementById('btnSaveSelfInject');", start_idx)

if start_idx != -1 and end_idx != -1:
    old_block = text[start_idx:end_idx]
    new_block = r"""var sStock = state.stokisStock.filter(st => String(st.StokisID || st.stokisId) === String(myStokis.ID || myStokis.id));\
                var aggregatedStock = {};\
                sStock.forEach(st => {\
                    var pId = st.ProductID || st.product;\
                    if (!aggregatedStock[pId]) aggregatedStock[pId] = 0;\
                    aggregatedStock[pId] += parseInt(st.Qty || st.qty) || 0;\
                });\
                var hasStock = false;\
                for (var k in aggregatedStock) { if(aggregatedStock[k] > 0) hasStock = true; }\
                \
                if (!hasStock) {\
                    pList.innerHTML = '\x3cp class="text-xs italic text-gray-400">Gudang stokis Anda kosong.\x3c\\/p>';\
                } else {\
                    for (var prodId in aggregatedStock) {\
                        var qty = aggregatedStock[prodId];\
                        if (qty <= 0) continue;\
                        var prodName = state.adminProducts.find(p => (p.ID || p.id) === prodId)?.Name || prodId;\
                        pList.innerHTML += `\
                        \x3cdiv class="flex justify-between items-center p-3 border border-gray-100 rounded-lg hover:bg-gray-50 transition-colors bg-white">\
                            \x3cdiv class="flex-1 pr-2">\
                                \x3ch4 class="font-bold text-xs text-gray-800 uppercase leading-tight">${prodName}\x3c\\/h4>\
                                \x3cp class="text-[10px] text-gray-400 font-medium mt-1">Stok Tersedia: ${qty} PCS\x3c\\/p>\
                            \x3c\\/div>\
                            \x3cdiv class="w-16 flex-shrink-0">\
                                \x3cinput type="number" min="0" max="${qty}" placeholder="0" data-id="${prodId}" class="self-qty-input w-full text-xs font-black text-center text-blue-700 bg-gray-50 border border-gray-200 rounded px-1 py-1.5 focus:outline-none focus:border-blue-500 shadow-inner" oninput="updateSelfInjectCount(this)">\
                            \x3c\\/div>\
                        \x3c\\/div>`;\
                    }\
                }\
                \
                """
    text = text[:start_idx] + new_block + text[end_idx:]
    with open("index_readable.html", "w") as f:
        f.write(text)
    print("Patched!")
else:
    print("Not found")

