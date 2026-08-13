Here are the rules, formatted as a detailed, reusable prompt in Markdown, designed to guide any image-to-image generator (like Midjourney, DALL-E, Stable Diffusion) to create new UI screens that strictly follow your existing design style.
UI Design Style Guide Prompt
Image Reference: Use [IMAGE_0.png] as the primary visual reference for all design rules
Core Design Philosophy


Clean, Modern, Data-Driven: Focus on information density with high legibility.


Aesthetic: Neumorphic/Clean Minimalist hybrid with soft shadows and sharp typography.


Layout: Strict grid system with standard card modules and consistent, generous negative space.

Typography


Font Family: Use a clean, geometric sans-serif (e.g., Inter, SF Pro, Montserrat).


Hierarchy:


Large Section Titles: High weight, large size (e.g., "Dashboard" in image_0.png).


Sub-section / Card Titles: Bold, medium weight (e.g., "Page Views", "Total Profit").


Data Points: Very large, bold, high-contrast numbers (e.g., "16,431", "$446.7K").


Body / Labels: Standard weight, smaller size, medium contrast grey (e.g., "vs. 1,463 last period").

Color Palette


Primary Accent: Vibrant Royal Blue (#3B82F6) for active states, key icons, primary CTAs.


Backgrounds: Off-white (#F9FAFB to #F3F4F6) for the main workspace. Pure White (#FFFFFF) for cards.


Sidebar: Cool, very light blue/grey (#EDF2F7).


Text (Primary): Dark charcoal (#111827) for main text.


Text (Secondary): Medium grey (#6B7280) for labels and descriptions.


Status Colors:


Positive (Good): Mint Green (#10B981) with light background (#D1FAE5) for positive growth indicators.


Negative (Alert): Rose Pink (#EF4444) with light background (#FEE2E2) for negative metrics.

Component Styling (Visual Vocabulary)


Container Cards:


Must have a white background.


Must use very soft, low-opacity, broad-spread shadows (the neumorphic effect).


Must have slightly rounded corners (approx. 12px to 16px radius).


Buttons:


Primary (e.g., Export): Darker deep blue (#2563EB) with a solid fill.


Segmented Control / Active State: Lighter primary blue (#D1E9FE).


Inputs: Clean white search fields with integrated icons (like the command key icon and looking glass).


Icons: Thin, monochromatic, cool grey outline icons with soft shadows, except for specific active/color-coded cases.


Data Visualization:


Line Graphs: Primary blue lines on a clean grid.


Bar Charts: Primary blue bars with distinct, lighter grey/white bars for comparison.


Radial/Guage Charts: Segmented arc, e.g., the green percentage tracker.

Design Patterns & Specific Rules


Sidebar: Left-aligned, light grey background. Icons on the left, labels to their right. Active states must use a light blue pill-shaped background for the entire item.


The 'Best Selling Products' Table: Alternating cool grey and light gold text (for product and sales/rating, respectively), using thin divider lines.


The Bottom-Left Sidebar Callout: This is a distinct deep blue textured gradient card (e.g., "Upgrade to Premium!").

How to use this prompt
When you want to generate a new screen, copy the entire section above, then add a final section with specific screen content:
Example Extension to the Prompt:
[Insert the markdown block above]
New Content to Generate:


Task: Generate a new screen called "Inventory Management".


Sidebar: Keep the current sidebar structure, but set "Products" as the active (light blue background) state.


Header: Title is "Inventory Management". Replace "Add widget" and "Export" with a single "Stock Audit" button.


Card Layout:


Place a large "Low Stock Alerts" card (like the total profit card) with a bar chart of affected items and a data label for "Critical Items: 12".


Below it, a full-width table similar to "Best Selling Products", but listing "Item Name", "Current Stock", "Reorder Point", and a "Stock Status" column (using green/red badges).


A small card on the far right for "Recent Stock Movements" showing a small, thin vertical timeline.