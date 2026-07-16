import re

with open("Index.html", "r") as f:
    text = f.read()

# I need to add an Edit button to Users table.
# Let's see how Users table is rendered in HTML.
user_row_pattern = r'<td class="px-2 py-3">.*?openFormUser.*?</td>'
matches = re.findall(user_row_pattern, text, re.DOTALL)
print("Users:", matches)

product_row_pattern = r'<td class="px-2 py-3">.*?openFormProduct.*?</td>'
matches = re.findall(product_row_pattern, text, re.DOTALL)
print("Products:", matches)

