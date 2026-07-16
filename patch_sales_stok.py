with open("index_readable.html", "r") as f:
    text = f.read()

import re

old_block = r"(var sStock = state\.stokisStock\.filter\(st => String\(st\.StokisID \|\| st\.stokisId\) === String\(myStokis\.ID \|\| myStokis\.id\)\);\\)\s*(if \(sStock\.length === 0\) \{\\)\s*(pList\.innerHTML = '\\x3cp class=\"text-xs italic text-gray-400\">Gudang stokis Anda kosong\.\\x3c\\\\/p>';\\)\s*(\} else \{\\)\s*(sStock\.forEach\(st => \{\\)\s*(var prodName = state\.adminProducts\.find\(p => \(p\.ID \|\| p\.id\) === \(st\.ProductID \|\| st\.product\)\)\?\.Name \|\| \(st\.ProductID \|\| st\.product\);\\)\s*(var prodId = st\.ProductID \|\| st\.product;\\)\s*(pList\.innerHTML \+= `\\)\s*(\\x3cdiv class=\"flex justify-between items-center p-3 border border-gray-100 rounded-lg hover:bg-gray-50 transition-colors bg-white\">\\)\s*(\\x3cdiv class=\"flex-1 pr-2\">\\)\s*(\\x3ch4 class=\"font-bold text-xs text-gray-800 uppercase leading-tight\">\$\{prodName\}\\x3c\\\\/h4>\\)\s*(\\x3cp class=\"text-\[10px\] text-gray-400 font-medium mt-1\">Stok Tersedia: \$\{st\.Qty \|\| st\.qty \|\| 0\} PCS\\x3c\\\\/p>\\)\s*(\\x3c\\\\/div>\\)\s*(\\x3cdiv class=\"w-16 flex-shrink-0\">\\)\s*(\\x3cinput type=\"number\" min=\"0\" max=\"\$\{st\.Qty \|\| st\.qty\}\" placeholder=\"0\" data-id=\"\$\{prodId\}\" class=\"self-qty-input w-full text-xs font-black text-center text-blue-700 bg-gray-50 border border-gray-200 rounded px-1 py-1\.5 focus:outline-none focus:border-blue-500 shadow-inner\" oninput=\"updateSelfInjectCount\(this\)\">\\)\s*(\\x3c\\\\/div>\\)\s*(\\x3c\\\\/div>`;\\)\s*(\}\);\\)\s*(\}\\)"

new_code = r"""\1
                var aggregatedStock = {};\
                sStock.forEach(st => {\
                    var pId = st.ProductID || st.product;\
                    if (!aggregatedStock[pId]) aggregatedStock[pId] = 0;\
                    aggregatedStock[pId] += parseInt(st.Qty || st.qty) || 0;\
                });\
                let hasStock = false;\
                for (let pId in aggregatedStock) { if (aggregatedStock[pId] > 0) hasStock = true; }\
\
                \2
                    \3
                \4
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
                    if (!hasStock) pList.innerHTML = '\x3cp class="text-xs italic text-gray-400">Gudang stokis Anda kosong.\x3c\\/p>';\
                \}\
"""

if re.search(old_block, text, re.DOTALL):
    text = re.sub(old_block, new_code, text, flags=re.DOTALL)
    with open("index_readable.html", "w") as f:
        f.write(text)
    print("Patched self inject stock aggregation")
else:
    print("Regex failed to match")

