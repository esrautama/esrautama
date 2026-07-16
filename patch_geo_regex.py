import re
with open("index_readable.html", "r") as f:
    text = f.read()
    
# We want to add `{ enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }` as the 3rd arg to getCurrentPosition
# And add the fetch block inside the success callback.
    
pattern = r"(var geo = lat \+ \\\", \\\" \+ lon;\\)\s*(document\.getElementById\('nooGeotagVal'\)\.value = geo;\\)\s*(var btn = document\.getElementById\('btnGeotag'\);\\)\s*(btn\.innerHTML = '[^']*';\\)\s*(btn\.classList\.replace\('[^']*', '[^']*'\);\\)"

def repl(m):
    return (m.group(1) + "\n" + m.group(2) + "\n" + m.group(3) + "\n" + m.group(4) + "\n" + m.group(5) + "\n" +
           r"                    fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`).then(r=>r.json()).then(d=>{if(d&&d.display_name){var a=document.getElementById('nooAddress');a.value=a.value?a.value+', '+d.display_name:d.display_name;}}).catch(e=>console.log(e));\")")

new_text = re.sub(pattern, repl, text)

# Now add the 3rd argument to getCurrentPosition
# The end of the call is: `});\` or `});` after `Pastikan izin...`
pattern2 = r"(showMessage\('Error Geotag', 'Pastikan izin lokasi \(GPS\) diaktifkan di browser\\/HP Anda\.'\);\\)\s*(\}\);"
def repl2(m):
    return m.group(1) + "\n                }, {enableHighAccuracy:true, timeout:10000, maximumAge:0});"
new_text = re.sub(pattern2, repl2, new_text)

with open("index_readable.html", "w") as f:
    f.write(new_text)
print("Regex patched")
