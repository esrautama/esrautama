import re

with open("Index.html", "r") as f:
    html_text = f.read()

pattern = r"renderTable\('tableBodyOutlets',\s*state\.outlets,\s*o\s*=>\s*`<tr><td[^>]*>\$\{o\.Name\|\|o\.name\}</td><td[^>]*>\$\{o\.Address\|\|o\.address\}</td><td[^>]*>\$\{o\.Type\|\|o\.type\}</td></tr>`\);"

replacement_render_outlet = """renderTable('tableBodyOutlets', state.outlets, o => `<tr>
<td class="font-bold text-xs">${o.Name||o.name}</td>
<td class="text-xs">${o.Address||o.address}</td>
<td class="text-xs">${o.Type||o.type}</td>
<td class="text-center">
    <button onclick="openFormOutlet('${o.ID||o.id}', '${o.Name||o.name}', '${o.Address||o.address}', '${o.Type||o.type}')" class="text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 w-8 h-8 rounded-lg transition mr-1" title="Edit"><i class="fas fa-edit"></i></button>
    <button onclick="deleteOutletDb('${o.ID||o.id}', '${o.Name||o.name}')" class="text-red-500 hover:text-red-700 bg-red-50 hover:bg-red-100 w-8 h-8 rounded-lg transition" title="Hapus"><i class="fas fa-trash"></i></button>
</td>
</tr>`);"""

html_text = re.sub(pattern, replacement_render_outlet, html_text, flags=re.DOTALL)

with open("Index.html", "w") as f:
    f.write(html_text)
print("Regex replace applied")
