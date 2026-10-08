# AI Agent Instructions: UI Design System & Color Rules

You MUST strictly follow the design tokens and color scheme below when writing, refactoring, or generating any UI components, CSS, or Tailwind styles. Do NOT use arbitrary hex codes outside of this system.

## 1. Color Palette Tokens

| Category | Shade | Hex Code | Designated Role & Usage |
| :--- | :--- | :--- | :--- |
| **Primary** | Light | `#FDBA74` | Hover states for primary elements, soft active borders |
| **Primary** | Default | `#F97316` | **Main CTA buttons, primary actions, key active states** |
| **Primary** | Dark | `#C2410C` | Pressed states, high-contrast primary text |
| **Secondary** | Light | `#334155` | Secondary borders, muted elements, subtle text |
| **Secondary** | Default | `#0F172A` | **Headers, top navigation bars, trust section backgrounds** |
| **Secondary** | Dark | `#020617` | Main dark background, deep surfaces, dark mode base |
| **Tertiary** | Light | `#FCD34D` | Soft warning backgrounds, subtle notification badges |
| **Tertiary** | Default | `#F59E0B` | **Highlights, status badges, warnings, rating stars** |
| **Tertiary** | Dark | `#B45309` | Text on yellow/amber warning badges |
| **Neutral** | Light | `#CBD5E1` | Dividers, disabled state borders, light card backgrounds |
| **Neutral** | Default | `#64748B` | Subtitles, labels, secondary icons, placeholder text |
| **Neutral** | Dark | `#1E293B` | **Main body text, headings, dark card surfaces** |

## 2. Code Generation Rules

1. **Strict Hex Compliance:** Never invent random hex colors (e.g., `#FF0000` or `#000000`). Always map UI elements to the table above.
2. **Accessibility Standard:** Ensure text contrast complies with WCAG AA guidelines. Always use `Primary Dark` or `Neutral Dark` text over light backgrounds.
3. **Styling Integration:**
   - **Tailwind CSS:** Map colors to custom Tailwind tokens or standard classes (`orange-500` for `#F97316`, `slate-900` for `#0F172A`, `amber-500` for `#F59E0B`, `slate-500` for `#64748B`).
   - **CSS Variables:** Prefer using CSS variables such as `var(--primary-default)` or `var(--secondary-default)`.