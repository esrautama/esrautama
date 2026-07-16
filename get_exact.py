import re
with open("index_readable.html", "r") as f:
    text = f.read()
match = re.search(r"navigator\.geolocation\.getCurrentPosition\([\s\S]*?\}\);", text)
if match:
    with open("exact_geotag.txt", "w") as f:
        f.write(match.group(0))
    print("Saved")
