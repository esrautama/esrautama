import os

for fn in ['Index.html', 'index.html']:
    if os.path.exists(fn):
        print(f"Checking {fn}...")
        with open(fn, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Search for any gear, cog, config, settings, setup, etc. inside the login page
        # Let's locate the login page element
        import re
        login_screens = re.findall(r'<div[^>]*id=["\']loginScreen["\'][^>]*>(.*?)</div>\s*</div>', content, re.DOTALL)
        for idx, ls in enumerate(login_screens):
            print(f"Found loginScreen in {fn}, length: {len(ls)}")
            # check for settings keywords or icons
            for word in ['gear', 'cog', 'settings', 'config', 'setup', 'wrench', 'sliders', 'setupButton', 'configButton', 'fa-']:
                matches = re.findall(rf'[^<\n]*{word}[^>\n]*', ls, re.IGNORECASE)
                if matches:
                    print(f"  Match for '{word}':")
                    for m in matches[:5]:
                        print(f"    {m.strip()}")
