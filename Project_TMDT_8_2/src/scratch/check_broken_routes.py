import os
import re

SRC_DIR = r"d:\hoc tap\TMDT\src"

def check_all():
    issues = []
    
    for root, dirs, files in os.walk(SRC_DIR):
        for f in files:
            if not f.endswith(('.html', '.js')):
                continue
            path = os.path.join(root, f)
            rel = os.path.relpath(path, SRC_DIR)
            
            with open(path, 'r', encoding='utf-8', errors='ignore') as fp:
                txt = fp.read()
                
            # Links in href
            for href in re.findall(r'href\s*=\s*["\']([^"\']+)["\']', txt):
                if href.startswith(('http:', 'https:', '#', 'javascript:', 'mailto:', 'tel:', 'data:')) or '\\' in href:
                    continue
                clean = href.split('?')[0].split('#')[0]
                if not clean:
                    continue
                if clean.startswith('/'):
                    target_file = os.path.normpath(os.path.join(SRC_DIR, clean.lstrip('/\\')))
                else:
                    base_dir = root
                    if root.endswith('\\js'):
                        peer_html = os.path.normpath(os.path.join(root, '..', 'html'))
                        if os.path.exists(peer_html):
                            base_dir = peer_html
                    target_file = os.path.normpath(os.path.join(base_dir, clean))
                if not os.path.exists(target_file):
                    issues.append((rel, f"href='{href}'", target_file))

            # location.href
            for loc in re.findall(r'location(?:\.href)?\s*=\s*[`"\']([^`"\']+)["\']', txt):
                if loc.startswith(('http:', 'https:', '#', 'javascript:')):
                    continue
                clean = loc.split('?')[0].split('#')[0]
                if not clean or '${' in clean:
                    continue
                # For JS files, determine if path is relative to html or js
                # In browser, relative JS location.href resolves relative to the HTML page hosting it!
                # If HTML page is in same or parent directory:
                html_dir = root
                if root.endswith('\\js'):
                    html_dir = os.path.normpath(os.path.join(root, '..', 'html'))
                    if not os.path.exists(html_dir):
                        html_dir = os.path.normpath(os.path.join(root, '..'))
                
                target_file = os.path.normpath(os.path.join(html_dir, clean))
                if not os.path.exists(target_file):
                    issues.append((rel, f"location.href='{loc}'", target_file))

            # JS mapping tables like pathMap
            for key, val in re.findall(r'["\']([a-zA-Z0-9_\-]+)["\']\s*:\s*["\']([^"\']+\.html[^"\']*)["\']', txt):
                clean = val.split('?')[0].split('#')[0]
                html_dir = root
                if root.endswith('\\js'):
                    html_dir = os.path.normpath(os.path.join(root, '..', 'html'))
                    if not os.path.exists(html_dir):
                        html_dir = os.path.normpath(os.path.join(root, '..'))
                target_file = os.path.normpath(os.path.join(html_dir, clean))
                if not os.path.exists(target_file):
                    issues.append((rel, f"map[{key}]='{val}'", target_file))

    print(f"Total issues found: {len(issues)}")
    for source, expr, target in issues:
        print(f"[{source}] -> {expr} => Target NOT FOUND: {target}")

if __name__ == '__main__':
    check_all()
