import os
import re
from urllib.parse import urlparse, unquote

SRC_DIR = r"d:\hoc tap\TMDT\src"

def check_all_links():
    print(f"Scanning {SRC_DIR} for broken links...")
    broken_links = []
    total_links = 0
    scanned_files = 0

    for root, dirs, files in os.walk(SRC_DIR):
        for f in files:
            if not f.endswith(('.html', '.js')):
                continue
            scanned_files += 1
            filepath = os.path.join(root, f)
            rel_file = os.path.relpath(filepath, SRC_DIR)

            with open(filepath, 'r', encoding='utf-8', errors='ignore') as file_obj:
                content = file_obj.read()

            # 1. Regex for href, src
            matches = re.findall(r'(?:href|src)=["\']([^"\']+)["\']', content)
            # 2. Regex for window.location[.href]
            matches += re.findall(r'window\.location(?:\.href)?\s*=\s*[`"\']([^`"\']+)["\']', content)
            # 3. Regex for fetch(...)
            matches += re.findall(r'fetch\(["\']([^"\']+)["\']\)', content)
            # 4. Regex for path mappings in JS: "key": "path"
            matches += re.findall(r'["\'][a-zA-Z0-9_\-]+["\']\s*:\s*["\']([^"\']+\.html[^"\']*)["\']', content)

            for target in matches:
                total_links += 1
                target = target.strip()
                # Ignore anchors, javascript:, mailto:, tel:, external http/https, data:, CDN
                if (target.startswith('#') or target.startswith('javascript:') or 
                    target.startswith('mailto:') or target.startswith('tel:') or 
                    target.startswith('data:') or target.startswith('http://') or 
                    target.startswith('https://') or target.startswith('//')):
                    continue
                
                # Strip query strings and hash
                clean_target = target.split('?')[0].split('#')[0]
                if not clean_target:
                    continue
                
                # Check target file resolution
                if clean_target.startswith('/'):
                    resolved = os.path.normpath(os.path.join(SRC_DIR, clean_target.lstrip('/\\')))
                else:
                    resolved = os.path.normpath(os.path.join(root, clean_target))

                if not os.path.exists(resolved):
                    broken_links.append({
                        'file': rel_file,
                        'raw': target,
                        'clean': clean_target,
                        'resolved': os.path.relpath(resolved, SRC_DIR) if resolved.startswith(SRC_DIR) else resolved
                    })

    print(f"\nScanned {scanned_files} files, {total_links} link references.")
    print(f"Found {len(broken_links)} broken references:")
    for b in broken_links:
        print(f"  [{b['file']}] -> '{b['raw']}' (Resolved as: {b['resolved']})")

    # Group by raw target to see patterns
    from collections import Counter
    target_counts = Counter(b['raw'] for b in broken_links)
    print("\nPatterns of broken links:")
    for t, cnt in target_counts.most_common():
        print(f"  {cnt}x '{t}'")

if __name__ == '__main__':
    check_all_links()
