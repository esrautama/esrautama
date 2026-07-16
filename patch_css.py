with open("index_readable.html", "r") as f:
    text = f.read()

import re

mobile_admin_css = """
        /* --- MOBILE ADMIN STYLES --- */
        body.admin-desktop .sales-only { display: none !important; }
        body.admin-desktop .admin-only { display: flex !important; }
        
        body.admin-desktop #mainNav {
            overflow-x: auto;
            justify-content: flex-start;
            flex-wrap: nowrap;
        }
        body.admin-desktop .admin-logo-area { display: none !important; }
        body.admin-desktop .nav-btn.admin-only {
            flex-direction: column;
            min-width: 75px;
            align-items: center;
            justify-content: center;
            color: #9ca3af;
            font-size: 10px;
        }
        body.admin-desktop .nav-btn.admin-only i {
            font-size: 1.25rem;
            margin-bottom: 4px;
        }
        body.admin-desktop .nav-btn.admin-only span {
            font-size: 10px;
            font-weight: 500;
        }
        body.admin-desktop .nav-btn.active-admin-nav {
            color: #3b82f6 !important;
        }
        
        @media (min-width: 768px) {
"""

text = text.replace("@media (min-width: 768px) {", mobile_admin_css)

with open("index_readable.html", "w") as f:
    f.write(text)
print("Patched CSS")
