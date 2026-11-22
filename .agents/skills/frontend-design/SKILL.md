---
name: frontend-design
description: Comprehensive frontend UI/UX design and engineering guidelines inspired by Claude's frontend design craft. Use to build beautiful, responsive, accessible, and polished user interfaces in Android Jetpack Compose and modern web.
---

# Frontend UI/UX Design & Craft Guidelines

This skill provides an authoritative guide to crafting modern, premium, and human-centric user interfaces.

---

## 1. Visual Hierarchy & Typography
- **Scale & Contrast**: Always maintain a distinct scale between Headlines, Titles, Body, and Labels. Avoid monotone text where headings blend into paragraph copy.
- **Font Weight Intentionality**:
  - `FontWeight.Bold` / `SemiBold` for primary page headers and active card titles.
  - `FontWeight.Medium` for interactive labels, buttons, and navigation pills.
  - `FontWeight.Normal` with adjusted alpha (e.g., `0.7f`–`0.8f`) for secondary descriptions and timestamps.
- **Line Heights**: Ensure comfortable vertical cadence. Headers need tighter line spacing (1.1–1.2x), while body copy needs room to breathe (1.4–1.6x).

---

## 2. Color Theory & Surface Depth
- **Avoid Pure Black & Raw White**:
  - Dark Theme: Use Slate/Zinc tones (`#0B0F19`, `#111827`, `#1E293B`) rather than pure `#000000`. This adds depth and softens eye strain.
  - Light Theme: Use Off-White/Canvas tones (`#F8FAFC`, `#F1F5F9`) rather than stark `#FFFFFF` everywhere.
- **Tonal Elevation**:
  - Background -> Surface -> Surface Variant -> Card/Container.
  - Use subtle 1dp borders (`outlineVariant.copy(alpha = 0.4f)`) to define card boundaries cleanly on OLED and LCD screens.
- **Accent & Primary Identity**:
  - Use vibrant, confident primary colors (e.g., Indigo `#6366F1`, Violet `#8B5CF6`, Cyan `#06B6D4`) paired with gradients on key calls-to-action (CTAs).
  - Use soft tonal containers for status tags: e.g., a green tag with a soft 15% opacity green background and rich green text.

---

## 3. Micro-Interactions, Animation & Feedback
- **State Transitions**:
  - Never let UI elements snap into existence abruptly. Wrap dynamically loaded content in `AnimatedVisibility(enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically())`.
  - Use `AnimatedContent` when toggling between Empty, Loading, and Populated states.
- **Progressive Feedback**:
  - Loading states should inform: show percentage, bytes transferred, or animated pulse bars rather than generic static spinners.
  - Use subtle haptic/visual ripple effects on all clickable elements.

---

## 4. Spacing, Rhythm & Layout Ergonomics
- **8dp Spatial Cadence**: Keep spacing consistent: `4.dp`, `8.dp`, `12.dp`, `16.dp`, `20.dp`, `24.dp`, `32.dp`.
- **Corner Radii Consistency**: Standardize squircles:
  - Small chips & badges: `8.dp`–`10.dp`
  - Action buttons & text fields: `12.dp`–`14.dp`
  - Cards & bottom sheets: `16.dp`–`24.dp`
- **Reachability (Thumb-Zone)**:
  - Place primary actionable controls (Save, Paste, Filter) in the lower half of the screen for one-handed mobile ergonomics.

---

## 5. Jetpack Compose Implementation Patterns
- Use `Scaffold` with edge-to-edge padding and `WindowInsets`.
- Use `Modifier.clip(RoundedCornerShape(...))` followed by `Modifier.background(...)` to prevent jagged edges.
- Use `Coil` async image loading with a smooth crossfade and subtle placeholder shimmer.
- Handle all empty, loading, error, and partial states gracefully with contextual illustrations and actions.

