with open("index_readable.html", "r") as f:
    text = f.read()

import re

old_block = r"(const listStok = document\.getElementById\('listStokStokis'\);\\).*?(listStok\.innerHTML = html;\\\s*\} else \{\\\s*listStok\.innerHTML = '\\x3cspan class=\"italic text-gray-400\">Belum ada stok di Stokis\. Silakan inject dari gudang di bawah ini\.\\x3c\\\\/span>';\\\s*\}\\)"

new_code = r"""\1
            if (state.stokisStock && state.stokisStock.length > 0) {\
                let grouped = {};\
                state.stokisStock.forEach(st => {\
                    let sId = st.StokisID || st.stokisId;\
                    let pId = st.ProductID || st.product;\
                    let qty = parseInt(st.Qty || st.qty) || 0;\
                    if(!grouped[sId]) grouped[sId] = {};\
                    if(!grouped[sId][pId]) grouped[sId][pId] = 0;\
                    grouped[sId][pId] += qty;\
                });\
\
                let html = '\x3cdiv class="space-y-3">';\
                var todayDate = new Date().toLocaleDateString('id-ID', {day: '2-digit', month: 'short', year: 'numeric'});\
\
                for(let sId in grouped) {\
                    let stokisName = state.stokisMaster.find(sm => (sm.ID || sm.id) === sId)?.Name || sId;\
                    html += `\
                    \x3cdiv class="border border-gray-200 rounded-xl bg-white shadow-sm overflow-hidden">\
                        \x3cdiv class="p-4 bg-gray-50 flex justify-between items-center cursor-pointer hover:bg-gray-100 transition" onclick="toggleCollapse('col-stk-${sId}', 'icon-stk-${sId}')">\
                            \x3cdiv>\
                                \x3ch4 class="font-bold text-gray-800 text-sm">${stokisName}\x3c\\/h4>\
                                \x3cspan class="text-[9px] text-gray-500">Update Terakhir: ${todayDate}\x3c\\/span>\
                            \x3c\\/div>\
                            \x3ci id="icon-stk-${sId}" class="fas fa-chevron-down text-gray-400 transition-transform">\x3c\\/i>\
                        \x3c\\/div>\
                        \x3cdiv id="col-stk-${sId}" class="hidden p-4 space-y-2 border-t border-gray-200 collapse-content">\
                    `;\
                    let hasStock = false;\
                    for(let pId in grouped[sId]) {\
                        let qty = grouped[sId][pId];\
                        if (qty <= 0) continue;\
                        hasStock = true;\
                        let prodName = state.adminProducts.find(p => (p.ID || p.id) === pId)?.Name || pId;\
                        html += `\
                            \x3cdiv class="flex justify-between items-center border-b border-gray-100 last:border-0 pb-2 last:pb-0">\
                                \x3cspan class="font-bold text-gray-700 text-xs">${prodName}\x3c\\/span>\
                                \x3cspan class="font-black text-blue-600 bg-blue-50 px-2 py-1 rounded text-xs">${qty} PCS\x3c\\/span>\
                            \x3c\\/div>\
                        `;\
                    }\
                    if (!hasStock) html += `\x3cspan class="italic text-gray-400 text-xs">Stok kosong\x3c\\/span>`;\
                    html += `\x3c\\/div>\x3c\\/div>`;\
                }\
                \2"""

if re.search(old_block, text, re.DOTALL):
    text = re.sub(old_block, new_code, text, flags=re.DOTALL)
    with open("index_readable.html", "w") as f:
        f.write(text)
    print("Patched listStokStokis")
else:
    print("Regex failed to match")

