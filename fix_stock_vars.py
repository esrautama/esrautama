import re

with open("Index.html", "r") as f:
    html = f.read()

# Replace p.Stock || p.stock with p.WarehouseStock || p.warehouseStock || p.Stock || p.stock
# And replace p.Stock||p.stock with p.WarehouseStock||p.warehouseStock||p.Stock||p.stock

html = html.replace("p.Stock || p.stock", "p.WarehouseStock || p.warehouseStock || p.Stock || p.stock")
html = html.replace("p.Stock||p.stock", "p.WarehouseStock||p.warehouseStock||p.Stock||p.stock")

with open("Index.html", "w") as f:
    f.write(html)
print("Replaced stock variables")

