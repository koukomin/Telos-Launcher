# Telos Flat Adaptive & Rondo Icon Design Specification

This document defines the mandatory visual design rules for all Telos virtual app icons, launcher surfaces, and AI-generated artwork.

## 1. Rondo Flat Adaptive Principles
All Telos Virtual App icons must form a cohesive **Rondo Flat Adaptive** family:

1. **Flat Circular Base**:
   - Every background layer uses a clean, perfect flat circle:
     `pathData="M24,2A22,22 0 1,0 24,46A22,22 0 1,0 24,2Z"`
   - Fill with vibrant, flat Material You palette colors (`#4CAF50` Green, `#1E88E5` Blue, `#E91E63` Pink, `#FF9800` Amber, `#00897B` Teal, `#8E24AA` Purple).

2. **Subtle 45° Flat Long Shadow**:
   - Each icon features an angled 45-degree flat long-shadow path (`#20000000` / `#25000000`) extending from the centered vector glyph to the bottom-right circle edge:
     `pathData="M14,14 L32,32 L26,44 L10,28 Z"`

3. **Centered Vector Glyph**:
   - High-contrast, clean white (`#FFFFFF`) or 2-tone vector glyph centered strictly within the safe zone (`24x24dp` inner bounds).

4. **100% Adaptive Masking**:
   - Never bake rounded corners or squircle masks directly into the artwork. The Android system / Telos Launcher applies circle, squircle, or rounded-square masks dynamically.

---

## 2. Cyberpunk Neo-Candy Theme Option
When Cyberpunk / Neo-Candy mode is enabled in Icon Settings:
- Dark metallic circular background (`#121318`).
- Glowing neon vector line accent (`#00F2FE` Cyan, `#818CF8` Indigo, `#FF007F` Pink) with rounded stroke join (`cap="round"` / `join="round"`).

---

## 3. Virtual App Icons Catalog
- **Phone**: `#4CAF50` (Rondo Green) + White Call Receiver + Flat Shadow
- **Messages**: `#00897B` (Rondo Teal) + White Chat Bubble + Flat Shadow
- **Files**: `#FF9800` (Rondo Amber) + White Folder + Flat Shadow
- **Camera**: `#E91E63` (Rondo Pink) + White Camera Aperture + Flat Shadow
- **Akou (Audio Recognition)**: `#D81B60` (Rondo Deep Pink) + White Musical Note + Flat Shadow
- **Radio**: `#F4511E` (Rondo Coral) + White Radio Antenna + Flat Shadow
- **Music / Media**: `#8E24AA` (Rondo Purple) + White Headphones + Flat Shadow
- **Video / Stremio**: `#7B1FA2` (Rondo Deep Purple) + White Film Reel + Flat Shadow
- **Calculator**: `#FB8C00` (Rondo Orange) + White Math Grid + Flat Shadow
- **Calendar**: `#E53935` (Rondo Red) + White Calendar Badge + Flat Shadow
- **Clock**: `#009688` (Rondo Teal) + White Dial Hands + Flat Shadow
