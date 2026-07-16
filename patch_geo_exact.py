with open("index_readable.html", "r") as f:
    text = f.read()

import re
# Add fetch block
text = re.sub(
    r"(btn\.classList\.replace\('text-red-600', 'text-green-600'\);\\)",
    r"\1\n                    fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`).then(r=>r.json()).then(d=>{if(d&&d.display_name){var a=document.getElementById('nooAddress');a.value=a.value?a.value+', '+d.display_name:d.display_name;}}).catch(e=>console.log(e));\\",
    text
)

# Add options
text = re.sub(
    r"(showMessage\('Error Geotag', 'Pastikan izin lokasi \(GPS\) diaktifkan di browser\\/HP Anda\.'\);\\)\s*(\}\);\\)",
    r"\1\n                }, {enableHighAccuracy: true, timeout: 10000, maximumAge: 0});\\",
    text
)

with open("index_readable.html", "w") as f:
    f.write(text)
print("Regex replaced")
